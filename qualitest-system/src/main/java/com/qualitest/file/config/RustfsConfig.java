package com.qualitest.file.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

/**
 * 在 qualitest.rustfs.enabled=true 时注册 RustFS 的 S3 客户端与预签名器。
 * 客户端关闭由 Spring 管理 AutoCloseable Bean 生命周期完成。
 */
@Configuration
@ConditionalOnProperty(prefix = "qualitest.rustfs", name = "enabled", havingValue = "true")
public class RustfsConfig {

    private static final Logger log = LoggerFactory.getLogger(RustfsConfig.class);

    /**
     * 创建 path-style 的 S3 客户端，并尝试确认 bucket 存在（不存在则创建；失败只打日志不阻断启动）。
     */
    @Bean
    public S3Client rustfsS3Client(RustfsProperties properties) {
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .forcePathStyle(true)
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)))
                .build();
        ensureBucket(client, properties.getBucket());
        return client;
    }

    /**
     * 预签名下载用的 S3Presigner，与上传客户端共用同一套 endpoint 与密钥。
     */
    @Bean
    public S3Presigner rustfsS3Presigner(RustfsProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private static StaticCredentialsProvider credentials(RustfsProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }

    /** 探测或创建业务桶；网络异常不抛出，避免拖垮应用启动 */
    private void ensureBucket(S3Client client, String bucket) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("RustFS bucket 已存在: {}", bucket);
        } catch (NoSuchBucketException e) {
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("RustFS bucket 已创建: {}", bucket);
            } catch (S3Exception createEx) {
                log.warn("RustFS 创建 bucket 失败（不阻断启动）: {} - {}", bucket, createEx.getMessage());
            }
        } catch (Exception e) {
            log.warn("RustFS bucket 检查失败（不阻断启动）: {} - {}", bucket, e.getMessage());
        }
    }
}

