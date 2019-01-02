package com.thumbnail.generator.component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.BinaryUtils;
import com.thumbnail.generator.service.AmazonClientService;

import net.coobird.thumbnailator.Thumbnails;

@Component
public class ThumbnailGenerator {
	private static final Logger log = LoggerFactory.getLogger(ThumbnailGenerator.class);

	@Autowired
	private AmazonClientService amazonClient;

	@Value("${amazonProperties.bucketName}")
	private String bucketName;

	@Value("${amazonProperties.thumbnailBucketName}")
	private String thumbnailBucketName;


	public void convertImageToThumbnail() throws InterruptedException, IOException {
		String imageName = "Banking-Service-Operations-BIG.gif";
		S3Object object = amazonClient.getS3Client().getObject(bucketName, imageName);
		S3ObjectInputStream sis = object.getObjectContent();
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Thumbnails.of(sis)
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

		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		amazonClient.getS3Client().putObject(thumbnailBucketName,"bso_thumbnail.jpg", bais, objectMetadata);
		baos = null;
		bais = null;
	}
}
