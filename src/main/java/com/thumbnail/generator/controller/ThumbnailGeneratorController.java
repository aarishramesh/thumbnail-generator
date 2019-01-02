package com.thumbnail.generator.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.BinaryUtils;
import com.thumbnail.generator.service.AmazonClientService;

import net.coobird.thumbnailator.Thumbnails;

@RestController
@RequestMapping("/thumbnail-generator/")
public class ThumbnailGeneratorController {

	private AmazonClientService amazonClient;

	@Autowired
	ThumbnailGeneratorController(AmazonClientService amazonClient) {
		this.amazonClient = amazonClient;
	}

	@GetMapping("/heartbeat")
	public String heartBeat() {
		return "Up and running";
	}
	
	@PostMapping("/uploadFile")
	public String uploadFile(@RequestPart(value = "file") MultipartFile file) {
		return this.amazonClient.uploadFile(file);
	}

	@GetMapping("/generateThumbnail")
	public void generateThumbnail() throws IOException {
		String imageName = "natureHighRes.jpg";
		S3Object object = amazonClient.getS3Client().getObject("previewappimages", imageName);
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
		amazonClient.getS3Client().putObject("previewappthumbnail","natureHighRes_thumbnail.jpg", bais, objectMetadata);
		baos = null;
		bais = null;
	}
	
	@GetMapping("/viewThumbnail")
	public void viewThumbnail(HttpServletResponse response) throws IOException {
		S3ObjectInputStream is = this.amazonClient.getFileFromS3Bucket();
		response.getOutputStream().write(is.readAllBytes());
	}
}
