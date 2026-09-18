package com.qualitest.common.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 读取 UTF-8 文本，以及按 --- 分隔线截取可复制正文。
 */
public final class ClasspathMarkdownSupport {

    private ClasspathMarkdownSupport() {
    }

    /**
     * 按路径读取 classpath UTF-8 原文；失败抛业务异常。
     *
     * @param path  classpath 相对路径
     * @param label 用于异常文案的资源说明
     * @return 文件原文
     */
    public static String loadClasspathUtf8(String path, String label) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return IoUtil.read(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServiceException("无法读取" + label + ": " + e.getMessage());
        }
    }

    /**
     * 读取 classpath 文本并规范化换行；空白则抛业务异常。
     * 返回值以单个换行结尾。
     *
     * @param path  classpath 相对路径
     * @param label 用于异常文案的资源说明
     * @return 规范化后的全文
     */
    public static String loadClasspathUtf8Normalized(String path, String label) {
        String text = loadClasspathUtf8(path, label);
        if (StrUtil.isBlank(text)) {
            throw new ServiceException(label + "为空");
        }
        return text.replace("\r\n", "\n").trim() + "\n";
    }

    /**
     * 若存在独立一行的 --- 分隔线，取其后内容作为可复制正文；否则取全文。
     * 分隔线后若为空则报错。
     *
     * @param raw   资源全文
     * @param label 用于异常文案的资源说明（如「可测提示词」）
     * @return 可复制正文
     */
    public static String extractCopyablePrompt(String raw, String label) {
        if (StrUtil.isBlank(raw)) {
            throw new ServiceException(label + "为空");
        }
        String normalized = raw.replace("\r\n", "\n");
        int sep = normalized.indexOf("\n---\n");
        String body = sep >= 0 ? normalized.substring(sep + "\n---\n".length()) : normalized;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            throw new ServiceException(label + "正文为空");
        }
        return trimmed;
    }
}
