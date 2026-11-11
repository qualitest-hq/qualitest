package com.qualitest.file.storage;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.StringUtils;
import com.qualitest.file.config.RustfsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * RustFS 存储实现。通过 S3 兼容接口读写对象，公开访问地址由 public-base-url 或 endpoint/bucket 拼接。
 */
@Component
public class RustfsFileHandler implements FileHandler {

    /** 预签名下载链接有效期 */
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(30);

    private final RustfsProperties rustfsProperties;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;

    public RustfsFileHandler(RustfsProperties rustfsProperties,
                             ObjectProvider<S3Client> s3ClientProvider,
                             ObjectProvider<S3Presigner> s3PresignerProvider) {
        this.rustfsProperties = rustfsProperties;
        this.s3ClientProvider = s3ClientProvider;
        this.s3PresignerProvider = s3PresignerProvider;
    }

    @Override
    public StorageType type() {
        return StorageType.RUSTFS;
    }

    @Override
    public void ensureReady() {
        requireClient();
    }

    /**
     * 将文件 Put 到配置的 bucket，返回公开访问 URL 作为落库路径。
     */
    @Override
    public UploadResult upload(MultipartFile file, String objectKey) {
        S3Client s3 = requireClient();
        String contentType = StringUtils.isNotEmpty(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        try {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(rustfsProperties.getBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();
            try (InputStream in = file.getInputStream()) {
                s3.putObject(put, RequestBody.fromInputStream(in, file.getSize()));
            }
            return UploadResult.of(file, objectKey, publicUrl(objectKey), StorageType.RUSTFS);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("RustFS 上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除桶内对象；失败时抛出业务异常。
     */
    @Override
    public void delete(String objectKey) {
        S3Client s3 = requireClient();
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(rustfsProperties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (Exception e) {
            throw new ServiceException("RustFS 删除失败: " + e.getMessage());
        }
    }

    /**
     * 查询对象元信息，并在预签名器可用时附带临时下载地址。对象不存在返回 empty。
     */
    @Override
    public Optional<ObjectMeta> head(String objectKey) {
        S3Client s3 = requireClient();
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(rustfsProperties.getBucket())
                    .key(objectKey)
                    .build());
            String presigned = null;
            S3Presigner presigner = s3PresignerProvider.getIfAvailable();
            if (presigner != null) {
                PresignedGetObjectRequest presignedReq = presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(PRESIGN_TTL)
                                .getObjectRequest(b -> b.bucket(rustfsProperties.getBucket()).key(objectKey))
                                .build());
                presigned = presignedReq.url().toString();
            }
            Instant lastMod = head.lastModified();
            return Optional.of(new ObjectMeta(
                    objectKey,
                    publicUrl(objectKey),
                    presigned,
                    head.contentLength(),
                    head.contentType(),
                    StorageType.RUSTFS.code(),
                    lastMod != null ? Date.from(lastMod) : null));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new ServiceException("RustFS 查询失败: " + e.getMessage());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("RustFS 查询失败: " + e.getMessage());
        }
    }

    /**
     * 拼接对象公开访问 URL。优先使用 public-base-url，否则为 endpoint/bucket/objectKey。
     */
    private String publicUrl(String objectKey) {
        String base = rustfsProperties.getPublicBaseUrl();
        if (StringUtils.isEmpty(base)) {
            base = rustfsProperties.getEndpoint() + "/" + rustfsProperties.getBucket();
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + objectKey;
    }

    /**
     * 取得已启用的 S3 客户端；未启用或 Bean 未创建时抛出业务异常。
     */
    private S3Client requireClient() {
        if (!rustfsProperties.isEnabled()) {
            throw new ServiceException("RustFS 未启用（请设置 qualitest.rustfs.enabled=true）");
        }
        S3Client s3 = s3ClientProvider.getIfAvailable();
        if (s3 == null) {
            throw new ServiceException("RustFS S3Client 不可用（请检查 endpoint 与凭据）");
        }
        return s3;
    }
}

