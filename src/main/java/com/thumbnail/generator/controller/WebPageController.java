package com.thumbnail.generator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebPageController {

	@GetMapping("")
	public String uploadImage(ModelMap model) {
		//model.put("name", getLoggedinUserName());
		return "upload";
	}
	
	@GetMapping("thumbnail")
	public String viewThumbnail(ModelMap model) {
		//model.put("name", getLoggedinUserName());
		return "viewThumbnail";
	}
	
	@GetMapping("image")
	public String viewImage(ModelMap model) {
		//model.put("name", getLoggedinUserName());
		return "viewOriginal";
	}
}
