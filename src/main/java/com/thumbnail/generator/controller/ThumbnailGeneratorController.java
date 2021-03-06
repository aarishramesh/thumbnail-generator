package com.thumbnail.generator.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.thumbnail.generator.service.ThumbnailGeneratorService;

import ch.qos.logback.classic.Logger;

@Controller
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
	public String uploadFile(@RequestPart(value = "file") MultipartFile file, 
			RedirectAttributes redirectAttributes) {
		if (file.isEmpty()) {
			redirectAttributes.addFlashAttribute("message", "Please select a file to upload");
			return "redirect:uploadStatus";
		} else if (!service.validateImageFileBeforeUpload(file)) {
			redirectAttributes.addFlashAttribute("message", "Please select an image file to upload");
			return "redirect:uploadStatus";
		}

		boolean uploadStatus = this.service.uploadFile(file);
		if (uploadStatus)
			redirectAttributes.addFlashAttribute("message",
					"You successfully uploaded '" + file.getOriginalFilename() + "'");
		else
			redirectAttributes.addFlashAttribute("message",
					"Upload of '" + file.getOriginalFilename() + "' has failed for some reason. Please try again"
							+ ". If the issue persists then try with a different file" );
		return "redirect:uploadStatus";
	}

	@GetMapping("uploadStatus")
	public String uploadStatus() {
		return "uploadStatus";
	}

	@GetMapping("image-view")
	public String viewThumbnail(@NonNull @NotEmpty @RequestParam(value = "filename") String fileName,
			@NotEmpty @RequestParam(value = "isThumbnail") String isThumbnail, HttpServletResponse response
			, RedirectAttributes redirectAttributes) {
		boolean isThumbnailBool = false;
		if (isThumbnail.equals("true"))
			isThumbnailBool = true;
		S3Object obj = this.service.getFileFromS3Bucket(fileName, isThumbnailBool);
		if (obj != null) {
			S3ObjectInputStream is = obj.getObjectContent();
			if (is != null) {
				try {
					byte[] bytes = IOUtils.toByteArray(is);
					response.getOutputStream().write(bytes);
					response.getOutputStream().flush();
					response.getOutputStream().close();
					return null;
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
				redirectAttributes.addFlashAttribute("message",
						"Error occurred while fetching '" + fileName);
				return "redirect:viewFailedStatus";
			} else {
				redirectAttributes.addFlashAttribute("message",
						"File '" + fileName + "' not found");
				return "redirect:viewFailedStatus";
			}
		} else {
			redirectAttributes.addFlashAttribute("message",
					"File '" + fileName + "' not found");
			return "redirect:viewFailedStatus";
		}
	}
	
	@GetMapping("viewFailedStatus")
	public String viewFailedStatus() {
		return "viewFailedStatus";
	}
}
