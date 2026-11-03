package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSON;
import com.qualitest.api.util.ExpectedResponseKindSupport;
import com.qualitest.api.util.MediaContentTypes;
import com.qualitest.flow.script.tool.ScriptCodecSupport;

import java.util.Map;

/**
 * HTTP 响应正文解析：把 JSON / XML 转成可按字段访问的对象，其余保留原文。
 * <p>
 * 处理顺序：
 * <ol>
 *   <li>空白正文 → body 为 null，形态 nonJson</li>
 *   <li>以大括号或方括号开头 → 按 JSON 解析；失败则保留原文</li>
 *   <li>满足 XML 条件 → 按 XML 解析为对象（去掉根节点、同名节点转数组、叶子取文本含 CDATA、忽略属性、拒绝 DOCTYPE）；失败则保留原文</li>
 *   <li>其余 → 保留原文字符串，形态 nonJson</li>
 * </ol>
 * XML 判定：Content-Type 为 text/xml、application/xml、application/*+xml；
 * 或 Content-Type 缺失 / 为 text/plain，且正文以 {@code <?xml}、{@code <xml>}、{@code <xml } 开头。
 * text/html 等其它类型不会按 XML 解析。解析失败不抛异常。
 */
public final class HttpResponseBodyParser {

    /** 纯文本 Content-Type（可配合正文嗅探尝试 XML） */
    private static final String TEXT_PLAIN = "text/plain";

    /** 工具类，不允许实例化 */
    private HttpResponseBodyParser() {
    }

    /**
     * 解析响应正文。
     *
     * @param bodyText 响应原文；空白时 body 为 null、kind 为 nonJson
     * @param headers  响应头，用于读取 Content-Type；可空
     * @return 解析后的 body 与实际响应形态（json / xml / nonJson）
     */
    public static Parsed parse(String bodyText, Map<String, String> headers) {
        if (bodyText == null || bodyText.isBlank()) {
            return new Parsed(null, ExpectedResponseKindSupport.ACTUAL_NON_JSON);
        }
        String trimmed = stripBom(bodyText).trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                Object json = JSON.parse(trimmed);
                return new Parsed(json, ExpectedResponseKindSupport.detectActualKind(json));
            } catch (Exception ignored) {
                return new Parsed(bodyText, ExpectedResponseKindSupport.ACTUAL_NON_JSON);
            }
        }
        if (shouldTryXml(trimmed, MediaContentTypes.contentTypeFromHeaders(headers))) {
            try {
                return new Parsed(ScriptCodecSupport.xmlParse(trimmed), ExpectedResponseKindSupport.ACTUAL_XML);
            } catch (IllegalArgumentException ignored) {
                // 非法 XML 或含禁止的 DOCTYPE：保留原文
            }
        }
        return new Parsed(bodyText, ExpectedResponseKindSupport.ACTUAL_NON_JSON);
    }

    /**
     * 是否尝试按 XML 解析。
     * Content-Type 声明为 XML 时尝试；未声明或为 text/plain 时再看正文是否像 XML 报文。
     *
     * @param trimmed     去 BOM 与首尾空白后的正文
     * @param contentType 小写、无参数的 Content-Type；可空
     * @return 是否应调用 XML 解析
     */
    static boolean shouldTryXml(String trimmed, String contentType) {
        if (contentType != null && isXmlContentType(contentType)) {
            return true;
        }
        if (contentType != null && !contentType.isBlank() && !TEXT_PLAIN.equals(contentType)) {
            return false;
        }
        return trimmed.startsWith("<?xml")
                || trimmed.startsWith("<xml>")
                || trimmed.startsWith("<xml ");
    }

    /**
     * 判断 Content-Type 是否表示 XML。
     * 识别 text/xml、application/xml，以及以 +xml 结尾的类型。
     */
    private static boolean isXmlContentType(String contentType) {
        return "text/xml".equals(contentType)
                || "application/xml".equals(contentType)
                || contentType.endsWith("+xml");
    }

    /** 去掉开头的 UTF-8 BOM，避免嗅探失败 */
    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
    }

    /**
     * 单次解析结果。
     *
     * @param body 解析后的正文：JSON 对象/数组、XML 转换后的对象或文本、原文字符串；空正文为 null
     * @param kind 实际响应形态：json（JSON 对象/数组）、xml（XML 已转对象）、nonJson（原文或解析失败）
     */
    public record Parsed(Object body, String kind) {

        /** 是否按 XML 解析成功（kind 为 xml） */
        public boolean isXml() {
            return ExpectedResponseKindSupport.ACTUAL_XML.equals(kind);
        }
    }
}
