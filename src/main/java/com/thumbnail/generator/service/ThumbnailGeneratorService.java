package com.thumbnail.generator.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.activation.MimetypesFileTypeMap;
import javax.annotation.PostConstruct;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.BinaryUtils;

import ch.qos.logback.classic.Logger;
import net.coobird.thumbnailator.Thumbnails;

@Service
public class ThumbnailGeneratorService {

	private static final Logger log = (Logger) LoggerFactory.getLogger(ThumbnailGeneratorService.class.getName());

	private AmazonS3 imagesS3Client;

	private AmazonS3 thumbnailS3Client;

	@Value("${amazonProperties.endpointUrl}")
	private String endpointUrl;
	@Value("${amazonProperties.imagesBucketRegion}")
	private String imagesBucketRegion;
	@Value("${amazonProperties.thumbnailBucketRegion}")
	private String thumbnailBucketRegion;
	@Value("${amazonProperties.bucketName}")
	private String bucketName;
	@Value("${amazonProperties.thumbnailBucketName}")
	private String thumbnailBucketName;
	@Value("${amazonProperties.accessKey}")
	private String accessKey;
	@Value("${amazonProperties.secretKey}")
	private String secretKey;

	@PostConstruct
	private void initializeAmazonClient() {
		AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
		this.imagesS3Client = AmazonS3ClientBuilder.standard()
				.withClientConfiguration(new ClientConfiguration().withMaxConnections(100))
				.withRegion(imagesBucketRegion).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
		this.thumbnailS3Client = AmazonS3ClientBuilder.standard()
				.withClientConfiguration(new ClientConfiguration().withMaxConnections(100))
				.withRegion(thumbnailBucketRegion).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
		//this.s3Client = new AmazonS3Client(credentials);
	}

	public AmazonS3 getImagesS3Client() {
		return this.imagesS3Client;
	}

	public AmazonS3 getThumbnailS3Client() {
		return this.thumbnailS3Client;
	}

	public boolean uploadFile(MultipartFile multipartFile) {
		try {
			File file = convertMultiPartToFile(multipartFile);
			if (validateImageFileBeforeUpload(file)) {
				String fileName = generateFileName(multipartFile);
				uploadFileTos3bucket(fileName, file);
				file.delete();
				return true;
			} else {
				// Invalid file error needs to be returned
				//return 400 error code;
			}
		} catch (IOException ex) {
			log.error("Error while converting multipart file {} to file object", multipartFile.getName(), ex);
		} catch (Exception ex) {
			log.error("Error while uploading file {} to S3", multipartFile.getName(), ex);
		}
		return false;
	}

	private boolean validateImageFileBeforeUpload(File file) {
		String mimetype= new MimetypesFileTypeMap().getContentType(file);
		String type = mimetype.split("/")[0];
		if(type.equals("image"))
			return true;
		else 
			return false;
	}

	private File convertMultiPartToFile(MultipartFile file) throws IOException {
		File convFile = new File(file.getOriginalFilename());
		FileOutputStream fos = new FileOutputStream(convFile);
		fos.write(file.getBytes());
		fos.close();
		return convFile;
	}

	private String generateFileName(MultipartFile multiPart) {
		return multiPart.getOriginalFilename().replace(" ", "_");
	}

	private void uploadFileTos3bucket(String fileName, File file) {
		imagesS3Client.putObject(new PutObjectRequest(bucketName, fileName, file)
				.withCannedAcl(CannedAccessControlList.PublicRead));
	}

	public S3ObjectInputStream getFileFromS3Bucket(String fileName) {
		S3Object s3Obj = null;
		try {
			s3Obj = thumbnailS3Client.getObject(thumbnailBucketName, fileName + "_thumbnail.jpg");
			if (s3Obj.getObjectContent() != null)
				return s3Obj.getObjectContent();
			else {
				log.info("Thumbnail file {} not found in the bucket {} ", fileName, thumbnailBucketName);
			}

		} catch (AmazonServiceException ase ) {
			log.error("Error while fetching file from thumbnail bucket for {}", fileName, ase);
		}
		return null;
	}

	public MessageHandler generateThumbnail(MessageHeaders headers, Object obj) {
		System.out.println(Thread.currentThread().getName());
		InputStream is = null;
		try {
			System.out.println(headers.toString());
			System.out.println("Message received");
			is = (InputStream) obj;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			String fileInfoJsonStr = (String) headers.get("file_remoteFileInfo");
			System.out.println(fileInfoJsonStr);
			org.json.JSONObject fileInfoJson = new org.json.JSONObject(fileInfoJsonStr);
			Thumbnails.of(is)
			.size(640, 480)
			.outputFormat("jpg")
			.toOutputStream(baos);
			ObjectMetadata objectMetadata = new ObjectMetadata();
			objectMetadata.setContentLength(baos.size());

			try {
				MessageDigest messageDigest = MessageDigest.getInstance("MD5");
				String md5Digest = BinaryUtils.toBase64(messageDigest.digest(baos.toByteArray()));
				objectMetadata.setContentMD5(md5Digest);
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("MessageDigest could not be initialized because it uses an unknown algorithm", e);
			}
			System.out.println(" Uploading the converted thumbnail");
			System.out.println(fileInfoJson.get("filename"));
			String fileNamePrefix = ((String)fileInfoJson.get("filename")).split("\\.")[0];
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			getThumbnailS3Client().putObject(thumbnailBucketName, fileNamePrefix 
					+ "_thumbnail.jpg", bais
					, objectMetadata);
			System.out.println(" Upload complete");
			try {
				baos.close();
			} catch (Exception ex) {

			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return null;
	}
}
