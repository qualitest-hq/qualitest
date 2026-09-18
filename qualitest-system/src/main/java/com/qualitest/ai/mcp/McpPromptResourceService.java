package com.qualitest.ai.mcp;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.ClasspathMarkdownSupport;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加载并组装 MCP 造流规程：Prompt 列表与正文、Resource 正文、Cursor Skill 全文，以及规程版本指纹。
 * <p>
 * 造流硬规矩正文来自 classpath 的 CORE.md；勘察 / 修失败 / 同步 Skill 另有短文；
 * Cursor Skill 由 YAML 头 + 版本指纹 + CORE 正文拼接而成。
 */
@Service
public class McpPromptResourceService {

    /** Prompt 名：造流与修流硬规矩全文 */
    public static final String PROMPT_CORE = "qualitest_core";
    /** Prompt 名：只读勘察推荐步骤 */
    public static final String PROMPT_SURVEY = "qualitest_survey";
    /** Prompt 名：修失败与跑通步骤 */
    public static final String PROMPT_FIX_RUN = "qualitest_fix_run";
    /** Prompt 名：安装或更新业务仓本地 Cursor Skill 的操作说明 */
    public static final String PROMPT_SYNC_LOCAL_SKILL = "qualitest_sync_local_skill";

    /** Resource 地址：造流硬规矩 Markdown */
    public static final String RESOURCE_CORE_URI = "qualitest://docs/core";

    /** classpath：造流硬规矩 */
    private static final String CORE_PATH = "cursor-skill/qualitest/CORE.md";
    /** classpath：只读勘察短文 */
    private static final String SURVEY_PATH = "cursor-skill/qualitest/SURVEY.md";
    /** classpath：修失败短文 */
    private static final String FIX_RUN_PATH = "cursor-skill/qualitest/FIX_RUN.md";
    /** classpath：同步本地 Skill 操作说明 */
    private static final String SYNC_PATH = "cursor-skill/qualitest/SYNC_LOCAL_SKILL.md";
    /** classpath：Cursor Skill 的 YAML 头（不含规程正文） */
    private static final String SKILL_FRONTMATTER_PATH = "cursor-skill/qualitest/SKILL.md";

    /** Prompt 说明：造流硬规矩 */
    private static final String DESC_CORE =
            "质衡造流/修流硬规矩。业务仓本地 Skill 未过期时勿再获取本 Prompt。";
    /** Prompt 说明：只读勘察 */
    private static final String DESC_SURVEY =
            "只读勘察推荐顺序。业务仓本地 Skill 未过期时勿再获取本 Prompt。";
    /** Prompt 说明：修失败 */
    private static final String DESC_FIX_RUN =
            "修失败与跑通步骤（失败后再修最多 2 轮）。业务仓本地 Skill 未过期时勿再获取本 Prompt。";
    /** Prompt 说明：同步本地 Skill */
    private static final String DESC_SYNC =
            "安装或更新业务仓 .cursor/skills/qualitest/SKILL.md；先查规程版本，未过期则停止。";

    /** 已计算的规程版本指纹；未加载时为 null */
    private volatile String cachedGuideVersion;
    /** 已加载的造流硬规矩正文 */
    private volatile String cachedCore;
    /** 已加载的只读勘察短文 */
    private volatile String cachedSurvey;
    /** 已加载的修失败短文 */
    private volatile String cachedFixRun;
    /** 已加载的同步本地 Skill 说明 */
    private volatile String cachedSync;
    /** 已加载的 Cursor Skill YAML 头 */
    private volatile String cachedSkillFrontmatter;

    /**
     * 返回当前造流规程版本指纹（CORE、SURVEY、FIX_RUN 正文的 SHA-256 前 12 位十六进制）。
     * 供握手 serverInfo、工具回执与本地 Skill 文件中的 guideVersion 字段使用。
     *
     * @return 12 位十六进制指纹
     */
    public String guideVersion() {
        ensureTextsLoaded();
        return cachedGuideVersion;
    }

    /**
     * 返回 MCP prompts/list 的 Prompt 摘要列表（名称与说明；始终返回四条）。
     *
     * @return Prompt 元数据列表
     */
    public List<Map<String, Object>> listPrompts() {
        return List.of(
                promptMeta(PROMPT_CORE, DESC_CORE),
                promptMeta(PROMPT_SURVEY, DESC_SURVEY),
                promptMeta(PROMPT_FIX_RUN, DESC_FIX_RUN),
                promptMeta(PROMPT_SYNC_LOCAL_SKILL, DESC_SYNC));
    }

    /**
     * 按名称返回 MCP prompts/get 载荷：说明文字与一条 user 文本消息。
     *
     * @param name Prompt 名（qualitest_core 等）
     * @return description 与 messages
     */
    public Map<String, Object> getPrompt(String name) {
        if (name == null || name.isBlank()) {
            throw new ServiceException("缺少 prompt name");
        }
        ensureTextsLoaded();
        return switch (name.trim()) {
            case PROMPT_CORE -> promptPayload(DESC_CORE, cachedCore);
            case PROMPT_SURVEY -> promptPayload(DESC_SURVEY, cachedSurvey);
            case PROMPT_FIX_RUN -> promptPayload(DESC_FIX_RUN, cachedFixRun);
            case PROMPT_SYNC_LOCAL_SKILL -> promptPayload(DESC_SYNC, cachedSync);
            default -> throw new ServiceException("未知 prompt: " + name);
        };
    }

    /**
     * 返回 MCP resources/list 的资源摘要（造流硬规矩一条）。
     *
     * @return Resource 元数据列表
     */
    public List<Map<String, Object>> listResources() {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("uri", RESOURCE_CORE_URI);
        resource.put("name", "qualitest_core");
        resource.put("description", "质衡造流/修流硬规矩（Markdown）。");
        resource.put("mimeType", "text/markdown");
        return List.of(resource);
    }

    /**
     * 按 URI 返回 MCP resources/read 载荷（Markdown 正文）。
     *
     * @param uri 资源地址，目前仅支持 qualitest://docs/core
     * @return contents 数组
     */
    public Map<String, Object> readResource(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new ServiceException("缺少 resource uri");
        }
        if (!RESOURCE_CORE_URI.equals(uri.trim())) {
            throw new ServiceException("未知 resource uri: " + uri);
        }
        ensureTextsLoaded();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("uri", RESOURCE_CORE_URI);
        content.put("mimeType", "text/markdown");
        content.put("text", cachedCore);
        return Map.of("contents", List.of(content));
    }

    /**
     * 返回造流硬规矩 Markdown 正文（供 Web 各编辑器规程 Tab 拼接使用）。
     *
     * @return CORE.md 全文
     */
    public String loadCoreText() {
        ensureTextsLoaded();
        return cachedCore;
    }

    /**
     * 返回「同步/更新本地 Cursor Skill」操作说明正文（供 Web 示例提问与 Prompt 使用）。
     *
     * @return SYNC_LOCAL_SKILL.md 全文
     */
    public String loadSyncLocalSkillText() {
        ensureTextsLoaded();
        return cachedSync;
    }

    /**
     * 返回可保存为业务仓 Cursor Skill 的完整 Markdown：
     * YAML 头（含 guideVersion）+ 造流硬规矩正文。
     *
     * @return 完整 SKILL.md 文本
     */
    public String loadCursorSkillText() {
        ensureTextsLoaded();
        return composeCursorSkill(cachedSkillFrontmatter, cachedGuideVersion, cachedCore);
    }

    /**
     * 首次调用时从 classpath 读入全部规程文本并计算版本指纹，之后直接使用缓存。
     */
    private void ensureTextsLoaded() {
        if (cachedGuideVersion != null) {
            return;
        }
        synchronized (this) {
            if (cachedGuideVersion != null) {
                return;
            }
            cachedCore = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(CORE_PATH, "造流规程 CORE");
            cachedSurvey = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(SURVEY_PATH, "勘察规程 SURVEY");
            cachedFixRun = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(FIX_RUN_PATH, "修失败规程 FIX_RUN");
            cachedSync = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(SYNC_PATH, "同步 Skill 规程");
            cachedSkillFrontmatter = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(
                    SKILL_FRONTMATTER_PATH, "Cursor Skill frontmatter");
            cachedGuideVersion = fingerprint(cachedCore + cachedSurvey + cachedFixRun);
        }
    }

    /**
     * 将版本指纹写入 YAML 头，再接上造流硬规矩正文，生成完整 Cursor Skill 文件内容。
     *
     * @param frontmatter  仅含 name、description 等字段的 YAML 头（以 --- 起止）
     * @param guideVersion 规程版本指纹
     * @param core         造流硬规矩正文
     * @return 完整 Markdown
     */
    static String composeCursorSkill(String frontmatter, String guideVersion, String core) {
        String fm = frontmatter == null ? "" : frontmatter.replace("\r\n", "\n").trim();
        String body = core == null ? "" : core.replace("\r\n", "\n").trim();
        if (fm.isEmpty()) {
            throw new ServiceException("Cursor Skill frontmatter 为空");
        }
        if (body.isEmpty()) {
            throw new ServiceException("造流规程 CORE 为空");
        }
        int close = fm.lastIndexOf("\n---");
        if (close < 0 && fm.equals("---")) {
            close = 0;
        }
        if (close < 0) {
            throw new ServiceException("Cursor Skill frontmatter 缺少结束 ---");
        }
        String yaml = fm.substring(0, close).trim();
        // 去掉已有 guideVersion 行后写入当前指纹
        StringBuilder yamlOut = new StringBuilder();
        for (String line : yaml.split("\n", -1)) {
            if (line.startsWith("guideVersion:")) {
                continue;
            }
            yamlOut.append(line).append('\n');
        }
        while (!yamlOut.isEmpty() && yamlOut.charAt(yamlOut.length() - 1) == '\n') {
            yamlOut.setLength(yamlOut.length() - 1);
        }
        yamlOut.append('\n').append("guideVersion: ").append(guideVersion).append('\n');
        return yamlOut + "---\n\n" + body + "\n";
    }

    /**
     * 组装 prompts/list 单条元数据。
     *
     * @param name        Prompt 名
     * @param description 简短说明
     * @return name、description 映射
     */
    private static Map<String, Object> promptMeta(String name, String description) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", name);
        meta.put("description", description);
        return meta;
    }

    /**
     * 组装 prompts/get 返回体：说明 + 一条 role=user 的文本消息。
     *
     * @param description Prompt 说明
     * @param text        正文
     * @return description、messages
     */
    private static Map<String, Object> promptPayload(String description, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", text);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("description", description);
        payload.put("messages", List.of(message));
        return payload;
    }

    /**
     * 计算文本的 SHA-256，取前 12 位十六进制作为规程版本指纹。
     *
     * @param text 参与指纹的正文拼接
     * @return 12 位十六进制字符串
     */
    static String fingerprint(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
