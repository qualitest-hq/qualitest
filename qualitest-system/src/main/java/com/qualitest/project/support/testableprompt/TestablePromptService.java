package com.qualitest.project.support.testableprompt;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 可测提示词服务：从 classpath 读取增量/全量 Markdown，截取可复制正文供前端一键复制。
 * 正文贴到 Cursor 等编辑器，让其按业务仓汇总可回贴质衡造流的短提示。
 */
@Service
public class TestablePromptService {

    /** 增量：刚改完单个功能时用 */
    public static final String KIND_INCREMENTAL = "incremental";
    /** 全量：整仓补测、按模块列出时可测提示 */
    public static final String KIND_FULL = "full";

    /**
     * 按类型返回可复制正文（去掉资源文件开头的说明头）。
     *
     * @param kind incremental 或 full
     * @return 提示词正文
     */
    public String loadPromptText(String kind) {
        String normalized = StrUtil.trimToEmpty(kind).toLowerCase();
        if (KIND_INCREMENTAL.equals(normalized)) {
            return extractCopyablePrompt(
                    loadClasspathUtf8("testable-prompt/AI_PROMPT_INCREMENTAL.md", "增量可测提示词"));
        }
        if (KIND_FULL.equals(normalized)) {
            return extractCopyablePrompt(
                    loadClasspathUtf8("testable-prompt/AI_PROMPT_FULL.md", "全量可测提示词"));
        }
        throw new ServiceException("不支持的可测提示词类型: " + kind);
    }

    /** 以 UTF-8 读取 classpath 资源全文 */
    private static String loadClasspathUtf8(String path, String label) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return IoUtil.read(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServiceException("无法读取" + label + ": " + e.getMessage());
        }
    }

    /**
     * 若存在独立一行的 --- 分隔线，取其后内容作为可复制正文；否则取全文。
     * 分隔线后若为空则报错。
     */
    static String extractCopyablePrompt(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new ServiceException("可测提示词为空");
        }
        String normalized = raw.replace("\r\n", "\n");
        int sep = normalized.indexOf("\n---\n");
        String body = sep >= 0 ? normalized.substring(sep + "\n---\n".length()) : normalized;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            throw new ServiceException("可测提示词正文为空");
        }
        return trimmed;
    }
}
