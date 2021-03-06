package com.thumbnail.generator.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.aws.inbound.S3StreamingMessageSource;
import org.springframework.integration.aws.support.S3RemoteFileTemplate;
import org.springframework.integration.aws.support.S3SessionFactory;
import org.springframework.integration.aws.support.filters.S3SimplePatternFileListFilter;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.scheduling.annotation.EnableAsync;

import com.thumbnail.generator.service.ThumbnailGeneratorService;

/**
 * Async Integration component which handles polling S3 for new objects matching the defined regex every 2 seconds 
 * 	and handles the delegation of S3 image thumbnail generation task to the service class 
 *
 */
@Configuration
@EnableIntegration
@IntegrationComponentScan
@EnableAsync
public class S3PollerConfiguration {
		
	@Value("${amazonProperties.bucketName}")
	private String bucketName;

	@Value("${amazonProperties.thumbnailBucketName}")
	private String thumbnailBucketName;

	@Autowired
	private ThumbnailGeneratorService thumbnailGeneratorService;

	public MessageSource<InputStream> s3InboundStreamingMessageSource() {  
		S3StreamingMessageSource messageSource = new S3StreamingMessageSource(template());
	    messageSource.setFilter(new S3SimplePatternFileListFilter("unprocessed*"));
		messageSource.setRemoteDirectory(bucketName);	
		return messageSource;
	}

	@Bean
	public S3RemoteFileTemplate template() {
		return new S3RemoteFileTemplate(new S3SessionFactory(thumbnailGeneratorService.getImagesS3Client()));
	}

	@Bean
	public PollableChannel s3FilesChannel() {
		return new QueueChannel();
	}
	
	@Bean
	IntegrationFlow fileReadingFlow() throws IOException {
		return IntegrationFlows
				.from(s3InboundStreamingMessageSource(),
						e -> e.poller(p -> p.fixedDelay(2, TimeUnit.SECONDS)))
				.handle(Message.class, (payload, header) -> thumbnailGeneratorService.generateThumbnail(payload.getHeaders(), payload.getPayload()))
				.get();
	}
}
