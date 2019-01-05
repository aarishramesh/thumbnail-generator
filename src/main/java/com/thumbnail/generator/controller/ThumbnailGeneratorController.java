package com.thumbnail.generator.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.thumbnail.generator.service.ThumbnailGeneratorService;

import ch.qos.logback.classic.Logger;

@RestController
@RequestMapping("thumbnail-generator")
public class ThumbnailGeneratorController {

	private static final Logger log = (Logger) LoggerFactory.getLogger(ThumbnailGeneratorController.class);
	
	@Autowired
	private ThumbnailGeneratorService service;

	@GetMapping("heart-beat")
	public String heartBeat() {
		return "Up and running";
	}

	@PostMapping("image-upload")
	public void uploadFile(@RequestPart(value = "file") MultipartFile file) {
		this.service.uploadFile(file);
	}

	@GetMapping("thumbnail-view/{file-name}")
	public void viewThumbnail(@NonNull @NotEmpty @PathVariable(value = "file-name") String fileName, HttpServletResponse response) {
		S3ObjectInputStream is = this.service.getFileFromS3Bucket(fileName);
		if (is != null) {
			try {
				response.getOutputStream().write(is.readAllBytes());
				response.setStatus(200);
			} catch (IOException e) {
				log.error("Error while writing file {} to response ",  fileName, e);
				response.setStatus(500);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			response.setStatus(400);
		}
	}
}
