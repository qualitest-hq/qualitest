package com.qualitest.file.storage;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.StringUtils;
import com.qualitest.file.config.QiniuProperties;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.Optional;

/**
 * 七牛云存储实现。服务端持有密钥代传，浏览器不直连七牛。
 */
@Component
public class QiniuFileHandler implements FileHandler {

    private final QiniuProperties properties;

    public QiniuFileHandler(QiniuProperties properties) {
        this.properties = properties;
    }

    @Override
    public StorageType type() {
        return StorageType.QINIU;
    }

    /**
     * 校验 accessKey、secretKey、bucket、domain 均已配置。
     */
    @Override
    public void ensureReady() {
        if (!properties.isReady()) {
            throw new ServiceException("七牛配置不完整（accessKey/secretKey/bucket/domain）");
        }
    }

    /**
     * 上传到七牛桶。若配置了 source 前缀，会加在对象键前面；返回 CDN 完整 URL。
     */
    @Override
    public UploadResult upload(MultipartFile file, String objectKey) {
        ensureReady();
        String key = applySourcePrefix(objectKey);
        String contentType = StringUtils.isNotEmpty(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        try {
            Auth auth = Auth.create(properties.getAccessKey(), properties.getSecretKey());
            String token = auth.uploadToken(properties.getBucket());
            UploadManager uploadManager = new UploadManager(buildConfiguration());
            Response response = uploadManager.put(file.getBytes(), key, token, null, contentType, false);
            if (!response.isOK()) {
                throw new ServiceException("七牛上传失败: " + response.bodyString());
            }
            return UploadResult.of(file, key, publicUrl(key), StorageType.QINIU);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("七牛上传失败: " + e.getMessage());
        }
    }

    /**
     * 按对象键删除七牛文件。
     */
    @Override
    public void delete(String objectKey) {
        ensureReady();
        try {
            Auth auth = Auth.create(properties.getAccessKey(), properties.getSecretKey());
            BucketManager bucketManager = new BucketManager(auth, buildConfiguration());
            bucketManager.delete(properties.getBucket(), objectKey);
        } catch (QiniuException e) {
            throw new ServiceException("七牛删除失败: " + e.getMessage());
        }
    }

    /**
     * 查询七牛对象元信息。状态码 612 视为不存在；其它错误抛出业务异常。
     */
    @Override
    public Optional<ObjectMeta> head(String objectKey) {
        ensureReady();
        try {
            Auth auth = Auth.create(properties.getAccessKey(), properties.getSecretKey());
            BucketManager bucketManager = new BucketManager(auth, buildConfiguration());
            FileInfo info = bucketManager.stat(properties.getBucket(), objectKey);
            Date lastMod = info.putTime > 0 ? new Date(info.putTime / 10000) : null;
            return Optional.of(new ObjectMeta(
                    objectKey,
                    publicUrl(objectKey),
                    null,
                    info.fsize,
                    info.mimeType,
                    StorageType.QINIU.code(),
                    lastMod));
        } catch (QiniuException e) {
            if (e.response != null && e.response.statusCode == 612) {
                return Optional.empty();
            }
            throw new ServiceException("七牛查询失败: " + e.getMessage());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("七牛查询失败: " + e.getMessage());
        }
    }

    /** 可选对象键前缀，来自配置 source */
    private String applySourcePrefix(String objectKey) {
        if (StringUtils.isEmpty(properties.getSource())) {
            return objectKey;
        }
        String source = properties.getSource().replaceAll("^/+|/+$", "");
        return source + "/" + objectKey;
    }

    /** 协议 + 域名 + 对象键，形成对外访问 URL */
    private String publicUrl(String objectKey) {
        String domain = properties.getDomain().replaceAll("^https?://", "").replaceAll("/+$", "");
        return properties.resolveProtocol() + domain + "/" + objectKey;
    }

    private Configuration buildConfiguration() {
        return new Configuration(resolveRegion(properties.getRegion()));
    }

    /**
     * 解析七牛区域代码；空或未知时使用自动区域。
     */
    private static Region resolveRegion(String region) {
        if (region == null || region.isBlank()) {
            return Region.autoRegion();
        }
        return switch (region.trim().toLowerCase()) {
            case "z0", "huadong" -> Region.huadong();
            case "z1", "huabei" -> Region.huabei();
            case "z2", "huanan" -> Region.huanan();
            case "na0", "beimei" -> Region.beimei();
            case "as0", "xinjiapo", "singapore" -> Region.xinjiapo();
            default -> Region.autoRegion();
        };
    }
}

