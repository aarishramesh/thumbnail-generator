package com.thumbnail.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ThumbnailGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ThumbnailGeneratorApplication.class, args);
	}
	
}
