package com.qualitest.file.storage;

import com.qualitest.common.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 统一上传结果。管理端通用上传与客户端文件接口均基于本结构再组装响应。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {

    /**
     * 建议入库的路径：本地为 /profile/upload/...，云存储为完整可访问 URL。
     */
    private String filePath;

    /**
     * 访问地址，当前实现与 filePath 相同，便于接口同时返回 url 与 fileName。
     */
    private String url;

    /**
     * 对象键：云存储桶内键，或本地 upload 目录下的相对路径。
     */
    private String objectKey;

    /** 文件字节数 */
    private long size;

    /** MIME 类型；未知时为 application/octet-stream */
    private String contentType;

    /** 浏览器侧原始文件名 */
    private String originalFilename;

    /** 实际使用的存储后端标识：local / rustfs / qiniu */
    private String storage;

    /**
     * 由上传文件与访问路径组装结果对象。
     *
     * @param file       原始上传文件
     * @param objectKey  对象键
     * @param accessPath 落库/访问路径
     * @param type       存储类型
     */
    public static UploadResult of(MultipartFile file, String objectKey, String accessPath, StorageType type) {
        String contentType = StringUtils.isNotEmpty(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        return UploadResult.builder()
                .filePath(accessPath)
                .url(accessPath)
                .objectKey(objectKey)
                .size(file.getSize())
                .contentType(contentType)
                .originalFilename(file.getOriginalFilename())
                .storage(type.code())
                .build();
    }
}

