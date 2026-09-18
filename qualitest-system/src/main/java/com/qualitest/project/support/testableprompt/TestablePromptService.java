package com.qualitest.project.support.testableprompt;

import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.ClasspathMarkdownSupport;
import org.springframework.stereotype.Service;

/**
 * 可测提示词服务：从 classpath 读取增量 / 指定范围 / 全量 Markdown，截取可复制正文。
 * 供顶栏 MCP 规程弹框的示例提问使用。
 */
@Service
public class TestablePromptService {

    /** 增量：刚改完单个功能时用 */
    public static final String KIND_INCREMENTAL = "incremental";
    /** 指定范围：整仓太大时只覆盖某模块 / 包 / 目录 */
    public static final String KIND_SCOPED = "scoped";
    /** 全量：整仓补测、按模块列出时可测提示 */
    public static final String KIND_FULL = "full";

    /**
     * 按类型返回可复制正文（去掉资源文件开头的说明头）。
     *
     * @param kind incremental、scoped 或 full
     * @return 提示词正文
     */
    public String loadPromptText(String kind) {
        String normalized = StrUtil.trimToEmpty(kind).toLowerCase();
        if (KIND_INCREMENTAL.equals(normalized)) {
            return ClasspathMarkdownSupport.extractCopyablePrompt(
                    ClasspathMarkdownSupport.loadClasspathUtf8(
                            "testable-prompt/AI_PROMPT_INCREMENTAL.md", "增量可测提示词"),
                    "可测提示词");
        }
        if (KIND_SCOPED.equals(normalized)) {
            return ClasspathMarkdownSupport.extractCopyablePrompt(
                    ClasspathMarkdownSupport.loadClasspathUtf8(
                            "testable-prompt/AI_PROMPT_SCOPED.md", "指定范围可测提示词"),
                    "可测提示词");
        }
        if (KIND_FULL.equals(normalized)) {
            return ClasspathMarkdownSupport.extractCopyablePrompt(
                    ClasspathMarkdownSupport.loadClasspathUtf8(
                            "testable-prompt/AI_PROMPT_FULL.md", "全量可测提示词"),
                    "可测提示词");
        }
        throw new ServiceException("不支持的可测提示词类型: " + kind);
    }
}
