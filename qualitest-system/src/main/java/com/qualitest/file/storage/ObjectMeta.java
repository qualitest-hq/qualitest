package com.qualitest.file.storage;

import java.util.Date;

/**
 * 存储层对象元信息。用于查询已上传文件的访问地址、大小、类型等，不直接作为 HTTP 响应体。
 *
 * @param objectKey      对象键（桶内路径或本地 upload 下相对路径）
 * @param url            可访问地址（本地为 /profile/upload/...，云为公开 URL）
 * @param presignedUrl   临时下载地址；仅部分云后端会生成，否则为空
 * @param size           字节大小
 * @param contentType    MIME 类型
 * @param storage        实际落盘后端标识（local / rustfs / qiniu）
 * @param lastModified   最后修改时间，可能为空
 */
public record ObjectMeta(
        String objectKey,
        String url,
        String presignedUrl,
        Long size,
        String contentType,
        String storage,
        Date lastModified) {
}

