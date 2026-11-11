package com.qualitest.file.storage;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.StringUtils;
import com.qualitest.common.utils.uuid.IdUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 生成与校验对象键。格式为：业务分类/日期/uuid_安全文件名。
 */
public final class ObjectKeyBuilder {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ObjectKeyBuilder() {
    }

    /**
     * 生成对象键。业务分类为空时使用 common。
     *
     * @param bizType           业务分类，如 avatar、product、common
     * @param originalFilename  原始文件名，用于后缀与可读名
     */
    public static String build(String bizType, String originalFilename) {
        String prefix = StringUtils.isNotEmpty(bizType) ? sanitizeSegment(bizType) : "common";
        String day = LocalDate.now().format(DAY_FMT);
        String safeName = sanitizeFilename(originalFilename);
        return prefix + "/" + day + "/" + IdUtils.fastSimpleUUID() + "_" + safeName;
    }

    /**
     * 校验对象键：非空、无绝对路径、无盘符、路径段不含 . 与 ..。
     *
     * @param objectKey 待校验键
     */
    public static void requireKey(String objectKey) {
        if (StringUtils.isEmpty(objectKey)) {
            throw new ServiceException("objectKey 不能为空");
        }
        String normalized = objectKey.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("://") || normalized.contains(":")) {
            throw new ServiceException("非法 objectKey");
        }
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new ServiceException("非法 objectKey");
            }
        }
    }

    /** 业务分类段清洗：去掉路径分隔符与危险字符 */
    private static String sanitizeSegment(String raw) {
        String s = raw.trim().replaceAll("[\\\\/]+", "-");
        s = s.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return StringUtils.isEmpty(s) ? "common" : s;
    }

    /** 文件名清洗：只保留末段，折叠 ..，限制长度 */
    private static String sanitizeFilename(String originalFilename) {
        if (StringUtils.isEmpty(originalFilename)) {
            return "file.bin";
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("..", "_");
        name = name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        if (name.length() > 80) {
            name = name.substring(name.length() - 80);
        }
        return StringUtils.isEmpty(name) ? "file.bin" : name;
    }
}

