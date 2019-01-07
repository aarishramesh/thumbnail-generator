package com.thumbnail.generator.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

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
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.BinaryUtils;

import ch.qos.logback.classic.Logger;
import net.coobird.thumbnailator.Thumbnails;

/**
 * Service class which handles the application logic for uploading image, viewing thumbnail, viewing image
 *
 */
@Service
public class ThumbnailGeneratorService {

	private static final Logger log = (Logger) LoggerFactory.getLogger(ThumbnailGeneratorService.class.getName());
	
	private ConcurrentHashMap<String, Boolean> imageVsProcessed = new ConcurrentHashMap<String, Boolean>();

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
			String fileName = generateFileName(multipartFile);
			uploadFileTos3bucket(fileName, file);
			file.delete();
			return true;

		} catch (IOException ex) {
			log.error("Error while converting multipart file {} to file object", multipartFile.getName(), ex);
		} catch (Exception ex) {
			log.error("Error while uploading file {} to S3", multipartFile.getName(), ex);
		}
		return false;
	}

	public boolean validateImageFileBeforeUpload(MultipartFile multipartFile) {
		try {
			File file = convertMultiPartToFile(multipartFile);
			String mimetype= new MimetypesFileTypeMap().getContentType(file);
			String type = mimetype.split("/")[0];
			if(type.equals("image"))
				return true;
			else 
				return false;
		} catch (Exception ex) {
			log.error("Error while converting multipart file {} to file object", multipartFile.getName(), ex);
		}
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
		return multiPart.getOriginalFilename();
	}

	private void uploadFileTos3bucket(String fileName, File file) {
		String newFileName = "unprocessed_" + fileName;
		imagesS3Client.putObject(new PutObjectRequest(bucketName, newFileName, file)
				.withCannedAcl(CannedAccessControlList.PublicRead));
		imageVsProcessed.put(fileName, false);
	}

	public S3Object getFileFromS3Bucket(String fileName, boolean isThumbnail) {
		S3Object s3Obj = null;
		try {
			if (isThumbnail) {
				s3Obj = thumbnailS3Client.getObject(thumbnailBucketName,  fileName);
			} else {
				Boolean isProcessed = imageVsProcessed.get(fileName);
				System.out.println(fileName + " : " + isProcessed);
				if (isProcessed != null && !isProcessed) {
					fileName = "unprocessed_" + fileName;
				}
				s3Obj = imagesS3Client.getObject(bucketName, fileName);
			}
			if (s3Obj.getObjectContent() != null)
				return s3Obj;
			else {
				log.info("file {} not found in the bucket {} ", fileName, thumbnailBucketName);
			}

		} catch (AmazonServiceException ase ) {
			log.error("Error while fetching file from thumbnail bucket for {}", fileName, ase);
		}
		return null;
	}
	
	public MessageHandler generateThumbnail(MessageHeaders headers, Object obj) {
		String origFileName = "";
		InputStream is = null;
		ByteArrayOutputStream baos = null;
		try {
			System.out.println(headers.toString());
			System.out.println("Message received");
			String fileInfoJsonStr = (String) headers.get("file_remoteFileInfo");
			System.out.println(fileInfoJsonStr);
			org.json.JSONObject fileInfoJson = new org.json.JSONObject(fileInfoJsonStr);
			String unprocessedFileName = ((String)fileInfoJson.get("filename"));
			origFileName = unprocessedFileName.substring(unprocessedFileName.indexOf("_") + 1, unprocessedFileName.length());
			
			// Generating thumbnail and write to the output stream for writing it to S3
			is = (InputStream) obj;
			baos = new ByteArrayOutputStream();
			Thumbnails.of(is)
			.size(640, 480)
			.outputFormat("jpg")
			.toOutputStream(baos);
			
			uploadToThumbnailS3Bucket(origFileName, unprocessedFileName, baos.toByteArray());
		} catch (SdkClientException sdkEx) { 
			log.error("Error from AWS while generating thumbnail for {}", origFileName, sdkEx);
		} catch (Exception ex) {
			log.error("Error while generating thumbnail for {}", origFileName, ex);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (baos != null) {
				try {
					baos.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}
	
	private void uploadToThumbnailS3Bucket(String origFileName, String unprocessedFileName, byte[] arr) 
		throws SdkClientException {
		ObjectMetadata objectMetadata = new ObjectMetadata();
		objectMetadata.setContentLength(arr.length);

		try {
			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			String md5Digest = BinaryUtils.toBase64(messageDigest.digest(arr));
			objectMetadata.setContentMD5(md5Digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("MessageDigest could not be initialized because it uses an unknown algorithm", e);
		}
	
		ByteArrayInputStream bais = new ByteArrayInputStream(arr);
		getThumbnailS3Client().putObject(thumbnailBucketName, origFileName, bais
				, objectMetadata);
		log.debug(" Upload complete");
		log.debug(origFileName);
		for (Entry entry : imageVsProcessed.entrySet()) {
			System.out.println(entry.getKey() + " : " + entry.getValue());
		}
		imageVsProcessed.put(origFileName, true);
		
		for (Entry entry : imageVsProcessed.entrySet()) {
			System.out.println(entry.getKey() + " : " + entry.getValue());
		}
		// Rename the file to original name
		CopyObjectRequest copyObjRequest = new CopyObjectRequest(bucketName, 
				unprocessedFileName, bucketName, origFileName);
		getImagesS3Client().copyObject(copyObjRequest);
		getImagesS3Client().deleteObject(new DeleteObjectRequest(bucketName, unprocessedFileName));
	}
}
