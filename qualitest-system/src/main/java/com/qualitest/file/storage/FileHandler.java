package com.qualitest.file.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * 单一存储后端的上传、删除与元信息查询。每种后端一个实现类，由统一存储服务按配置选用。
 */
public interface FileHandler {

    /**
     * 本处理器对应的存储类型。
     */
    StorageType type();

    /**
     * 检查当前后端是否可用。例如云存储未启用、密钥缺失、客户端未创建时抛出业务异常。
     */
    void ensureReady();

    /**
     * 按给定对象键上传文件。对象键由统一存储服务事先生成，本方法不再重新生成键。
     *
     * @param file      上传文件
     * @param objectKey 对象键
     * @return 上传结果（含落库路径、对象键、大小等）
     */
    UploadResult upload(MultipartFile file, String objectKey);

    /**
     * 按对象键删除文件。文件不存在或删除失败时抛出业务异常。
     *
     * @param objectKey 对象键
     */
    void delete(String objectKey);

    /**
     * 查询对象元信息。对象不存在时返回 empty；网络或鉴权等基础设施故障抛出业务异常。
     *
     * @param objectKey 对象键
     */
    Optional<ObjectMeta> head(String objectKey);
}

