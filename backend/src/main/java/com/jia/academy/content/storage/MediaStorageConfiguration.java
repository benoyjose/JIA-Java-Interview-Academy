package com.jia.academy.content.storage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(prefix = "jia.media.s3", name = "enabled", havingValue = "true")
public class MediaStorageConfiguration {
    @Bean
    S3Client mediaS3Client(@Value("${jia.media.s3.region:ap-south-1}") String region,
                           @Value("${jia.media.s3.endpoint:}") String endpoint) {
        var builder = S3Client.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    @Bean
    S3Presigner mediaS3Presigner(@Value("${jia.media.s3.region:ap-south-1}") String region,
                                 @Value("${jia.media.s3.endpoint:}") String endpoint) {
        var builder = S3Presigner.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }
}
