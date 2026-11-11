package com.qualitest.file.storage;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.StringUtils;
import com.qualitest.common.utils.file.FileUploadUtils;
import com.qualitest.common.utils.file.MimeTypeUtils;
import com.qualitest.file.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 统一文件存储入口。按 qualitest.storage.type 选择后端执行上传、查询与删除；失败直接抛错，不会改用其它后端。
 * <p>
 * 浏览器只把文件 POST 到应用接口，由本服务再写入本地盘或对象存储，密钥留在服务端。
 */
@Service
public class FileStorageService {

    private final StorageProperties storageProperties;
    /** 按存储类型索引的后端实现 */
    private final Map<StorageType, FileHandler> handlers;

    /**
     * 收集全部 FileHandler 实现，建成类型到处理器的映射。
     */
    public FileStorageService(StorageProperties storageProperties, List<FileHandler> handlerList) {
        this.storageProperties = storageProperties;
        Map<StorageType, FileHandler> map = new EnumMap<>(StorageType.class);
        for (FileHandler handler : handlerList) {
            map.put(handler.type(), handler);
        }
        this.handlers = Map.copyOf(map);
    }

    /**
     * 上传文件，业务分类固定为 common。
     */
    public UploadResult upload(MultipartFile file) {
        return upload(file, "common");
    }

    /**
     * 校验扩展名后，生成对象键并交给当前配置的后端上传。
     *
     * @param file    上传文件
     * @param bizType 业务分类，参与对象键前缀，可空
     */
    public UploadResult upload(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        try {
            FileUploadUtils.assertAllowed(file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
        FileHandler handler = currentHandler();
        handler.ensureReady();
        String objectKey = ObjectKeyBuilder.build(bizType, file.getOriginalFilename());
        return handler.upload(file, objectKey);
    }

    /**
     * 查询当前后端上的对象元信息；不存在则抛业务异常。
     */
    public ObjectMeta getObjectMeta(String objectKey) {
        ObjectKeyBuilder.requireKey(objectKey);
        FileHandler handler = currentHandler();
        handler.ensureReady();
        return handler.head(objectKey)
                .orElseThrow(() -> new ServiceException("文件不存在: " + objectKey));
    }

    /**
     * 删除当前后端上的对象。
     */
    public void delete(String objectKey) {
        ObjectKeyBuilder.requireKey(objectKey);
        FileHandler handler = currentHandler();
        handler.ensureReady();
        handler.delete(objectKey);
    }

    /**
     * 组装前端可访问地址：已是 http(s) 的原样返回；相对路径则拼接当前请求的服务根地址。
     *
     * @param filePath  落库路径或完整 URL
     * @param serverUrl 当前服务根，如 http://host:port
     */
    public String resolveAccessUrl(String filePath, String serverUrl) {
        if (StringUtils.isEmpty(filePath)) {
            return filePath;
        }
        if (isAbsoluteUrl(filePath)) {
            return filePath;
        }
        String base = serverUrl == null ? "" : serverUrl;
        if (base.endsWith("/") && filePath.startsWith("/")) {
            return base.substring(0, base.length() - 1) + filePath;
        }
        return base + filePath;
    }

    /**
     * 判断是否为 http 或 https 绝对地址（对象存储 CDN 回传路径即属此类）。
     */
    public static boolean isAbsoluteUrl(String path) {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    /** 按配置解析当前后端；未注册对应实现时抛错 */
    private FileHandler currentHandler() {
        StorageType type = storageProperties.resolvedType();
        FileHandler handler = handlers.get(type);
        if (handler == null) {
            throw new ServiceException("未注册存储后端: " + type);
        }
        return handler;
    }
}

