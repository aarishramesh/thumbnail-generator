package com.thumbnail.generator.config;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.aws.inbound.S3InboundFileSynchronizer;
import org.springframework.integration.aws.inbound.S3InboundFileSynchronizingMessageSource;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;
import org.springframework.scheduling.annotation.EnableAsync;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.BinaryUtils;
import com.thumbnail.generator.service.AmazonClientService;

import net.coobird.thumbnailator.Thumbnails;

@Configuration
@EnableIntegration
@IntegrationComponentScan
@EnableAsync
public class S3PollerConfiguration {
	@Value("${amazonProperties.bucketName}")
	private String bucketName;

	@Value("${amazonProperties.thumbnailBucketName}")
	private String thumbnailBucketName;

	public static final String OUTPUT_DIR2 = "target2";

	@Autowired
	private AmazonClientService amazonClient;

	@Bean
	public S3InboundFileSynchronizer s3InboundFileSynchronizer() {
		S3InboundFileSynchronizer synchronizer = new S3InboundFileSynchronizer(amazonClient.getS3Client());
		synchronizer.setPreserveTimestamp(true);
		synchronizer.setRemoteDirectory(bucketName);            
		return synchronizer;
	}

	@Bean
	@InboundChannelAdapter(value = "s3FilesChannel", poller = @Poller(fixedDelay = "5"))
	public S3InboundFileSynchronizingMessageSource s3InboundFileSynchronizingMessageSource() {
		S3InboundFileSynchronizingMessageSource messageSource =
				new S3InboundFileSynchronizingMessageSource(s3InboundFileSynchronizer());
		messageSource.setAutoCreateLocalDirectory(true);
		messageSource.setLocalDirectory(new File(OUTPUT_DIR2));
		messageSource.setLocalFilter(new AcceptOnceFileListFilter<File>());
		return messageSource;
	}

	@Bean
	public PollableChannel s3FilesChannel() {
		return new QueueChannel();
	}

	@Bean
	IntegrationFlow fileReadingFlow() {
		return IntegrationFlows
				.from(s3InboundFileSynchronizingMessageSource(),
						e -> e.poller(p -> p.fixedDelay(30, TimeUnit.SECONDS)))
				.handle(thumbnailGenerator())
				.get();
	}

	@Bean
	public MessageHandler thumbnailGenerator() {
		try {
			File imageFile = new File(OUTPUT_DIR2);
			System.out.println("Got the file :: " + imageFile.getName());
			FileWritingMessageHandler handler = new FileWritingMessageHandler(imageFile);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Thumbnails.of(new BufferedInputStream(new FileInputStream(imageFile)))
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
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			amazonClient.getS3Client().putObject(thumbnailBucketName,imageFile.getName() + "_thumbnail.jpg", bais
					, objectMetadata);
			baos = null;
			bais = null;
			imageFile.deleteOnExit();
			System.out.println(" Upload complete and deleted the file locally");
			handler.setExpectReply(false); // end of pipeline, reply not needed
			return handler;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
}