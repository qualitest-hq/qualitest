package com.qualitest.ai.mcp;

import com.qualitest.ai.mcp.McpToolInvokeService.McpProjectGates;
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
import java.util.regex.Pattern;

/**
 * MCP 造流规程加载与组装。
 * <p>
 * 从 classpath 读取造流硬规矩、勘察短文、修失败短文、同步 Skill 说明与 Cursor Skill YAML 头；
 * 按项目「允许 MCP 自动写流」「允许 MCP 自动跑流」「允许 MCP 导入接口」三道开关裁剪正文；
 * 计算规程源文指纹，并拼出带门控后缀的 guideVersion，供 Prompt / Resource / 本地 Skill 使用。
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

    /** guideVersion / 握手 version 在开启写流时追加的后缀 */
    public static final String SUFFIX_AUTO_WRITE = "+autowrite";
    /** guideVersion / 握手 version 在开启跑流时追加的后缀 */
    public static final String SUFFIX_AUTORUN = "+autorun";
    /** guideVersion / 握手 version 在开启导入接口时追加的后缀 */
    public static final String SUFFIX_IMPORT_APIS = "+importApis";

    /** classpath 路径：造流硬规矩 */
    private static final String CORE_PATH = "cursor-skill/qualitest/CORE.md";
    /** classpath 路径：只读勘察短文 */
    private static final String SURVEY_PATH = "cursor-skill/qualitest/SURVEY.md";
    /** classpath 路径：修失败短文 */
    private static final String FIX_RUN_PATH = "cursor-skill/qualitest/FIX_RUN.md";
    /** classpath 路径：同步本地 Skill 操作说明 */
    private static final String SYNC_PATH = "cursor-skill/qualitest/SYNC_LOCAL_SKILL.md";
    /** classpath 路径：Cursor Skill 的 YAML 头（不含规程正文） */
    private static final String SKILL_FRONTMATTER_PATH = "cursor-skill/qualitest/SKILL.md";

    /** 匹配写流门控块；关写流时整段（含起止标记）删除 */
    private static final Pattern GATE_AUTO_WRITE = Pattern.compile(
            "(?s)<!--\\s*mcp:autowrite\\s*-->.*?<!--\\s*/mcp:autowrite\\s*-->\\R?");
    /** 匹配跑流门控块；关跑流时整段（含起止标记）删除 */
    private static final Pattern GATE_AUTORUN = Pattern.compile(
            "(?s)<!--\\s*mcp:autorun\\s*-->.*?<!--\\s*/mcp:autorun\\s*-->\\R?");
    /** 匹配导入门控块；关导入时整段（含起止标记）删除 */
    private static final Pattern GATE_IMPORT = Pattern.compile(
            "(?s)<!--\\s*mcp:import\\s*-->.*?<!--\\s*/mcp:import\\s*-->\\R?");
    /** 匹配门控起止标记行；对应开关开启时只删标记、保留中间正文 */
    private static final Pattern GATE_MARKERS = Pattern.compile(
            "(?m)^\\s*<!--\\s*/?mcp:(?:autowrite|autorun|import)\\s*-->\\s*\\R?");

    /** prompts/list 中造流硬规矩的说明文字 */
    private static final String DESC_CORE =
            "质衡造流/修流硬规矩。业务仓本地 Skill 未过期时勿再获取本 Prompt。";
    /** prompts/list 中只读勘察的说明文字 */
    private static final String DESC_SURVEY =
            "只读勘察推荐顺序。业务仓本地 Skill 未过期时勿再获取本 Prompt。";
    /** prompts/list 中修失败的说明文字 */
    private static final String DESC_FIX_RUN =
            "修失败与跑通步骤（失败后再修最多 2 轮）。业务仓本地 Skill 未过期时勿再获取本 Prompt。";
    /** prompts/list 中同步本地 Skill 的说明文字 */
    private static final String DESC_SYNC =
            "安装或更新业务仓 .cursor/skills/qualitest/SKILL.md；先查规程版本，未过期则停止。";

    /** 规程源文指纹（裁剪前 CORE+SURVEY+FIX_RUN 的 hash 前 12 位）；未加载时为 null */
    private volatile String cachedBaseGuideVersion;
    /** 造流硬规矩源文（含门控 HTML 注释块） */
    private volatile String cachedCore;
    /** 只读勘察短文源文 */
    private volatile String cachedSurvey;
    /** 修失败短文源文 */
    private volatile String cachedFixRun;
    /** 同步本地 Skill 操作说明源文 */
    private volatile String cachedSync;
    /** Cursor Skill YAML 头源文 */
    private volatile String cachedSkillFrontmatter;

    /**
     * 返回规程源文指纹（不含开关后缀）。
     * 对 CORE、SURVEY、FIX_RUN 裁剪前全文做 SHA-256，取十六进制小写前 12 位。
     * 改规程 Markdown 会变；只改项目开关不变。
     *
     * @return 12 位十六进制指纹
     */
    public String baseGuideVersion() {
        ensureTextsLoaded();
        return cachedBaseGuideVersion;
    }

    /**
     * 返回合成规程版本串：源文指纹，再按开关追加 +autowrite、+autorun、+importApis。
     * 三道都关时仅为 12 位指纹。本地 Skill 文件中的 guideVersion 与本串全等则视为未过期。
     *
     * @param autoWriteEnabled  是否开启「允许 MCP 自动写流」
     * @param autorunEnabled    是否开启「允许 MCP 自动跑流」
     * @param importApisEnabled 是否开启「允许 MCP 导入接口」
     * @return 合成版本串
     */
    public String guideVersion(boolean autoWriteEnabled, boolean autorunEnabled, boolean importApisEnabled) {
        ensureTextsLoaded();
        return appendGateSuffixes(cachedBaseGuideVersion, autoWriteEnabled, autorunEnabled, importApisEnabled);
    }

    /**
     * 按项目门控快照返回合成规程版本串。
     *
     * @param gates 写流 / 跑流 / 导入三道开关快照；null 按只读
     * @return 合成版本串
     */
    public String guideVersion(McpProjectGates gates) {
        if (gates == null) {
            return guideVersion(false, false, false);
        }
        return guideVersion(gates.autoWriteEnabled(), gates.autorunEnabled(), gates.importApisEnabled());
    }

    /**
     * 在任意前缀后按开关追加门控后缀（先 +autowrite，再 +autorun，再 +importApis；未开则不加）。
     * 用于规程 guideVersion，也用于握手 serverInfo.version。
     *
     * @param base              前缀文本（规程指纹或协议版本号等）
     * @param autoWriteEnabled  写流开关是否开启
     * @param autorunEnabled    跑流开关是否开启
     * @param importApisEnabled 导入开关是否开启
     * @return 带可选后缀的合成串
     */
    public static String appendGateSuffixes(String base, boolean autoWriteEnabled,
                                            boolean autorunEnabled, boolean importApisEnabled) {
        StringBuilder sb = new StringBuilder(base == null ? "" : base);
        if (autoWriteEnabled) {
            sb.append(SUFFIX_AUTO_WRITE);
        }
        if (autorunEnabled) {
            sb.append(SUFFIX_AUTORUN);
        }
        if (importApisEnabled) {
            sb.append(SUFFIX_IMPORT_APIS);
        }
        return sb.toString();
    }

    /**
     * 按项目门控快照追加门控后缀。
     *
     * @param base  前缀文本
     * @param gates 写流 / 跑流 / 导入三道开关快照；null 按只读
     * @return 带可选后缀的合成串
     */
    public static String appendGateSuffixes(String base, McpProjectGates gates) {
        if (gates == null) {
            return appendGateSuffixes(base, false, false, false);
        }
        return appendGateSuffixes(base, gates.autoWriteEnabled(), gates.autorunEnabled(), gates.importApisEnabled());
    }

    /**
     * 按三道开关裁剪 Markdown 正文。
     * 关写流：删除 mcp:autowrite 整块；关跑流：删除 mcp:autorun 整块；关导入：删除 mcp:import 整块；
     * 开着的块只去掉起止注释行，保留中间文字；再压缩多余空行。
     *
     * @param markdown          含门控注释块的源文，可空
     * @param autoWriteEnabled  写流开关是否开启
     * @param autorunEnabled    跑流开关是否开启
     * @param importApisEnabled 导入开关是否开启
     * @return 裁剪后正文（末尾补换行）；入参 null 时返回空串
     */
    static String applyGates(String markdown, boolean autoWriteEnabled,
                             boolean autorunEnabled, boolean importApisEnabled) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown == null ? "" : markdown;
        }
        String text = markdown.replace("\r\n", "\n");
        if (!autoWriteEnabled) {
            text = GATE_AUTO_WRITE.matcher(text).replaceAll("");
        }
        if (!autorunEnabled) {
            text = GATE_AUTORUN.matcher(text).replaceAll("");
        }
        if (!importApisEnabled) {
            text = GATE_IMPORT.matcher(text).replaceAll("");
        }
        text = GATE_MARKERS.matcher(text).replaceAll("");
        // 删块后可能留下连续空行，压成最多一个空行
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim() + "\n";
    }

    /**
     * 返回 prompts/list 用的四条 Prompt 元数据（名称与说明）。
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
     * 按 Prompt 名称返回 prompts/get 载荷；正文按三道开关裁剪。
     * 未知名称或名称为空时抛业务异常。
     *
     * @param name              Prompt 名
     * @param autoWriteEnabled  写流开关是否开启
     * @param autorunEnabled    跑流开关是否开启
     * @param importApisEnabled 导入开关是否开启
     * @return 含 description 与 messages 的载荷
     */
    public Map<String, Object> getPrompt(String name, boolean autoWriteEnabled,
                                         boolean autorunEnabled, boolean importApisEnabled) {
        if (name == null || name.isBlank()) {
            throw new ServiceException("缺少 prompt name");
        }
        ensureTextsLoaded();
        return switch (name.trim()) {
            case PROMPT_CORE -> promptPayload(DESC_CORE,
                    applyGates(cachedCore, autoWriteEnabled, autorunEnabled, importApisEnabled));
            case PROMPT_SURVEY -> promptPayload(DESC_SURVEY,
                    applyGates(cachedSurvey, autoWriteEnabled, autorunEnabled, importApisEnabled));
            case PROMPT_FIX_RUN -> promptPayload(DESC_FIX_RUN,
                    applyGates(cachedFixRun, autoWriteEnabled, autorunEnabled, importApisEnabled));
            case PROMPT_SYNC_LOCAL_SKILL -> promptPayload(DESC_SYNC,
                    applyGates(cachedSync, autoWriteEnabled, autorunEnabled, importApisEnabled));
            default -> throw new ServiceException("未知 prompt: " + name);
        };
    }

    /**
     * 按 Prompt 名称与项目门控快照返回 prompts/get 载荷。
     *
     * @param name  Prompt 名
     * @param gates 写流 / 跑流 / 导入三道开关快照；null 按只读
     * @return 含 description 与 messages 的载荷
     */
    public Map<String, Object> getPrompt(String name, McpProjectGates gates) {
        if (gates == null) {
            return getPrompt(name, false, false, false);
        }
        return getPrompt(name, gates.autoWriteEnabled(), gates.autorunEnabled(), gates.importApisEnabled());
    }

    /**
     * 返回 resources/list 用的资源摘要（造流硬规矩一条）。
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
     * 按 URI 返回 resources/read 载荷；正文为按开关裁剪后的造流硬规矩。
     * 仅支持造流硬规矩那一条 URI；其它或空 URI 抛业务异常。
     *
     * @param uri               资源地址
     * @param autoWriteEnabled  写流开关是否开启
     * @param autorunEnabled    跑流开关是否开启
     * @param importApisEnabled 导入开关是否开启
     * @return 含 contents 数组的载荷
     */
    public Map<String, Object> readResource(String uri, boolean autoWriteEnabled,
                                            boolean autorunEnabled, boolean importApisEnabled) {
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
        content.put("text", applyGates(cachedCore, autoWriteEnabled, autorunEnabled, importApisEnabled));
        return Map.of("contents", List.of(content));
    }

    /**
     * 按 URI 与项目门控快照返回 resources/read 载荷。
     *
     * @param uri   资源地址
     * @param gates 写流 / 跑流 / 导入三道开关快照；null 按只读
     * @return 含 contents 数组的载荷
     */
    public Map<String, Object> readResource(String uri, McpProjectGates gates) {
        if (gates == null) {
            return readResource(uri, false, false, false);
        }
        return readResource(uri, gates.autoWriteEnabled(), gates.autorunEnabled(), gates.importApisEnabled());
    }

    /**
     * 返回按开关裁剪后的造流硬规矩正文（供 Web 各编辑器规程拼接）。
     *
     * @param autoWriteEnabled  写流开关是否开启
     * @param autorunEnabled    跑流开关是否开启
     * @param importApisEnabled 导入开关是否开启
     * @return 裁剪后的造流硬规矩 Markdown
     */
    public String loadCoreText(boolean autoWriteEnabled, boolean autorunEnabled, boolean importApisEnabled) {
        ensureTextsLoaded();
        return applyGates(cachedCore, autoWriteEnabled, autorunEnabled, importApisEnabled);
    }

    /**
     * 返回「同步/更新本地 Cursor Skill」操作说明全文。
     *
     * @return 同步 Skill 说明 Markdown
     */
    public String loadSyncLocalSkillText() {
        ensureTextsLoaded();
        return cachedSync;
    }

    /**
     * 组装完整 Cursor Skill 文件内容：
     * 按开关改写 YAML description、写入合成 guideVersion，再接上裁剪后的造流硬规矩正文。
     *
     * @param autoWriteEnabled  写流开关是否开启
     * @param autorunEnabled    跑流开关是否开启
     * @param importApisEnabled 导入开关是否开启
     * @return 可保存为 SKILL.md 的完整 Markdown
     */
    public String loadCursorSkillText(boolean autoWriteEnabled, boolean autorunEnabled,
                                      boolean importApisEnabled) {
        ensureTextsLoaded();
        String version = guideVersion(autoWriteEnabled, autorunEnabled, importApisEnabled);
        String core = applyGates(cachedCore, autoWriteEnabled, autorunEnabled, importApisEnabled);
        String frontmatter = applySkillFrontmatterGates(
                cachedSkillFrontmatter, autoWriteEnabled, autorunEnabled, importApisEnabled);
        return composeCursorSkill(frontmatter, version, core);
    }

    /**
     * 按开关重写 Skill YAML 中的 description 字段，只保留当前已开通能力相关的唤醒词。
     *
     * @param frontmatter       原始 YAML 头
     * @param autoWriteEnabled  写流开关是否开启
     * @param autorunEnabled    跑流开关是否开启
     * @param importApisEnabled 导入开关是否开启
     * @return 改写 description 后的 YAML 头；无 description 字段则原样返回
     */
    static String applySkillFrontmatterGates(String frontmatter, boolean autoWriteEnabled,
                                             boolean autorunEnabled, boolean importApisEnabled) {
        String fm = frontmatter == null ? "" : frontmatter.replace("\r\n", "\n");
        String description = buildSkillDescription(autoWriteEnabled, autorunEnabled, importApisEnabled);
        if (fm.contains("description:")) {
            int descStart = fm.indexOf("description:");
            int close = fm.lastIndexOf("\n---");
            if (close < 0) {
                close = fm.length();
            }
            String before = fm.substring(0, descStart);
            String after = fm.substring(close);
            return before + "description: >-\n  " + description.replace("\n", "\n  ") + "\n" + after.trim() + "\n";
        }
        return fm;
    }

    /**
     * 按开关拼出 Skill description 纯文本（不含 YAML 缩进）。
     * 开写流时带上改流相关词；开跑流时带上跑通相关词；开导入时带上导入相关词。
     *
     * @param autoWriteEnabled  写流开关是否开启
     * @param autorunEnabled    跑流开关是否开启
     * @param importApisEnabled 导入开关是否开启
     * @return description 正文
     */
    static String buildSkillDescription(boolean autoWriteEnabled, boolean autorunEnabled,
                                        boolean importApisEnabled) {
        StringBuilder sb = new StringBuilder(
                "经质衡（QualiTest）MCP 勘察");
        if (autoWriteEnabled) {
            sb.append("、改测试流");
        }
        if (autorunEnabled) {
            sb.append("并跑通");
        }
        sb.append("。在用户提到质衡 MCP、造流、修流、跑测试流");
        if (autoWriteEnabled) {
            sb.append("、submit、create_flow、update_flow_meta");
        }
        if (autorunEnabled) {
            sb.append("、run_test_flow");
        }
        if (importApisEnabled) {
            sb.append("、import_apis");
        }
        sb.append("、项目 Token、允许 MCP 自动写流");
        if (autorunEnabled) {
            sb.append("、允许 MCP 自动跑流");
        }
        if (importApisEnabled) {
            sb.append("、允许 MCP 导入接口");
        }
        sb.append("，或要在 Cursor 里直接");
        if (autoWriteEnabled) {
            sb.append("改质衡画布");
        } else {
            sb.append("勘察质衡测试流");
        }
        sb.append("时使用。");
        return sb.toString();
    }

    /**
     * 首次调用时从 classpath 读入全部规程文本并计算源文指纹，之后直接用内存缓存。
     */
    private void ensureTextsLoaded() {
        if (cachedBaseGuideVersion != null) {
            return;
        }
        synchronized (this) {
            if (cachedBaseGuideVersion != null) {
                return;
            }
            cachedCore = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(CORE_PATH, "造流规程 CORE");
            cachedSurvey = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(SURVEY_PATH, "勘察规程 SURVEY");
            cachedFixRun = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(FIX_RUN_PATH, "修失败规程 FIX_RUN");
            cachedSync = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(SYNC_PATH, "同步 Skill 规程");
            cachedSkillFrontmatter = ClasspathMarkdownSupport.loadClasspathUtf8Normalized(
                    SKILL_FRONTMATTER_PATH, "Cursor Skill frontmatter");
            cachedBaseGuideVersion = fingerprint(cachedCore + cachedSurvey + cachedFixRun);
        }
    }

    /**
     * 将合成 guideVersion 写入 YAML 头，再接上造流硬规矩正文，得到完整 Skill 文件。
     * 若头里已有 guideVersion 行会先去掉再写入当前值。
     *
     * @param frontmatter  YAML 头（以 --- 起止，含 name、description 等）
     * @param guideVersion 要写入的合成规程版本
     * @param core         已按开关裁剪的造流硬规矩正文
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
     * 组装 prompts/list 单条：name 与 description。
     *
     * @param name        Prompt 名
     * @param description 简短说明
     * @return 元数据映射
     */
    private static Map<String, Object> promptMeta(String name, String description) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", name);
        meta.put("description", description);
        return meta;
    }

    /**
     * 组装 prompts/get 返回体：说明文字 + 一条 role=user 的文本消息。
     *
     * @param description Prompt 说明
     * @param text        用户消息正文
     * @return 载荷映射
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
     * 对文本做 SHA-256，取十六进制小写前 12 位作为规程源文指纹。
     *
     * @param text 参与指纹的正文
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
