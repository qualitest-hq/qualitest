package com.qualitest.file.storage;

import com.qualitest.common.config.QualitestConfig;
import com.qualitest.common.constant.Constants;
import com.qualitest.common.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Optional;

/**
 * 本地磁盘存储实现。文件落在应用上传根目录（qualitest.profile/upload）下，按对象键分子目录保存。
 */
@Component
public class LocalFileHandler implements FileHandler {

    @Override
    public StorageType type() {
        return StorageType.LOCAL;
    }

    @Override
    public void ensureReady() {
        // 本地盘无外部依赖，始终视为就绪
    }

    /**
     * 将文件写入本地磁盘，返回以 /profile/upload/ 开头的相对访问路径。
     */
    @Override
    public UploadResult upload(MultipartFile file, String objectKey) {
        Path target = resolvePath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new ServiceException("本地保存文件失败: " + e.getMessage());
        }
        return UploadResult.of(file, objectKey, publicUrl(objectKey), StorageType.LOCAL);
    }

    /**
     * 删除本地文件；文件不存在时抛出业务异常。
     */
    @Override
    public void delete(String objectKey) {
        try {
            if (!Files.deleteIfExists(resolvePath(objectKey))) {
                throw new ServiceException("文件不存在或删除失败: " + objectKey);
            }
        } catch (IOException e) {
            throw new ServiceException("本地文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 读取本地文件大小、类型与修改时间；文件不存在返回 empty。
     */
    @Override
    public Optional<ObjectMeta> head(String objectKey) {
        Path local = resolvePath(objectKey);
        if (!Files.isRegularFile(local)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ObjectMeta(
                    objectKey,
                    publicUrl(objectKey),
                    null,
                    Files.size(local),
                    Files.probeContentType(local),
                    StorageType.LOCAL.code(),
                    Date.from(Files.getLastModifiedTime(local).toInstant())));
        } catch (IOException e) {
            throw new ServiceException("读取本地文件失败: " + e.getMessage());
        }
    }

    /**
     * 拼本地资源映射路径，供前端经应用静态资源前缀访问。
     */
    public String publicUrl(String objectKey) {
        return Constants.RESOURCE_PREFIX + "/upload/" + objectKey;
    }

    /**
     * 将对象键解析为绝对路径，并保证结果仍位于上传根目录内，防止目录穿越。
     */
    private Path resolvePath(String objectKey) {
        ObjectKeyBuilder.requireKey(objectKey);
        Path root = Paths.get(QualitestConfig.getUploadPath()).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey.replace('/', java.io.File.separatorChar)).normalize();
        if (!target.startsWith(root)) {
            throw new ServiceException("非法 objectKey");
        }
        return target;
    }
}

