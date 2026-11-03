package com.qualitest.flow.script.tool;

import com.qualitest.flow.script.ScriptValueConverter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 脚本编码与报文工具：Base64、URL 编解码、XML 解析与生成、签名原文拼接。
 */
public final class ScriptCodecSupport {

    /** xmlStringify 默认根节点名 */
    private static final String DEFAULT_XML_ROOT = "xml";

    /** 工具类，不允许实例化 */
    private ScriptCodecSupport() {
    }

    /** UTF-8 明文 → Base64 字符串。 */
    public static String base64Encode(String data) {
        return Base64.getEncoder().encodeToString(String.valueOf(data).getBytes(StandardCharsets.UTF_8));
    }

    /** Base64 字符串 → UTF-8 明文；非法输入抛运行时异常。 */
    public static String base64Decode(String data) {
        return new String(Base64.getDecoder().decode(String.valueOf(data)), StandardCharsets.UTF_8);
    }

    /** UTF-8 URL 编码（application/x-www-form-urlencoded，空格编码为 +）。 */
    public static String urlEncode(String data) {
        return URLEncoder.encode(String.valueOf(data), StandardCharsets.UTF_8);
    }

    /** UTF-8 URL 解码，+ 还原为空格。 */
    public static String urlDecode(String data) {
        return URLDecoder.decode(String.valueOf(data), StandardCharsets.UTF_8);
    }

    /**
     * 解析 XML 为对象：去掉根节点；有子元素的节点转对象，同名节点转数组，叶子节点取文本（含 CDATA），忽略属性。
     * 根节点无子元素时返回其文本。禁止 DOCTYPE，防 XXE。
     */
    public static Object xmlParse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("xmlParse 内容不能为空");
        }
        Document document;
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            document = builder.parse(new InputSource(new StringReader(text.trim())));
        } catch (Exception e) {
            throw new IllegalArgumentException("xmlParse 失败: " + e.getMessage(), e);
        }
        return elementToValue(document.getDocumentElement());
    }

    /**
     * 对象生成 XML：对象转子元素，数组转同名重复元素，null 转空元素。
     *
     * @param opts root（根节点名，默认 xml）、cdata（字符串值是否包 CDATA，默认 true）
     */
    public static String xmlStringify(Object value, Map<String, Object> opts) {
        String root = ScriptToolOptions.string(opts, "root", DEFAULT_XML_ROOT);
        boolean cdata = ScriptToolOptions.bool(opts, "cdata", true);
        StringBuilder out = new StringBuilder();
        appendElement(out, root, value, cdata);
        return out.toString();
    }

    /**
     * 拼接签名原文：按 key 的 ASCII 升序拼成 {@code k1=v1&k2=v2}，不做 URL 编码。
     * 对象 / 数组值序列化为 JSON。
     *
     * @param opts exclude（排除的 key，默认 ["sign"]）、skipEmpty（跳过 null 与空串，默认 true）
     */
    public static String signContent(Map<String, Object> params, Map<String, Object> opts) {
        if (params == null) {
            throw new IllegalArgumentException("signContent 参数必须是对象");
        }
        Set<String> exclude = ScriptToolOptions.stringSet(opts, "exclude", Set.of("sign"));
        boolean skipEmpty = ScriptToolOptions.bool(opts, "skipEmpty", true);
        Map<String, Object> sorted = new TreeMap<>(params);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (exclude.contains(entry.getKey())) {
                continue;
            }
            String text = valueText(entry.getValue());
            if (skipEmpty && (text == null || text.isEmpty())) {
                continue;
            }
            parts.add(entry.getKey() + "=" + (text == null ? "" : text));
        }
        return String.join("&", parts);
    }

    /** 创建安全的 XML 解析工厂：禁止 DOCTYPE、外部实体与 XInclude，不展开实体引用 */
    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        return factory;
    }

    /** 元素转值：无子元素返回文本；否则转对象，同名子元素合并为数组 */
    private static Object elementToValue(Element element) {
        List<Element> children = childElements(element);
        if (children.isEmpty()) {
            return element.getTextContent();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Element child : children) {
            String name = child.getTagName();
            Object value = elementToValue(child);
            Object existing = map.get(name);
            if (existing == null) {
                map.put(name, value);
            } else if (existing instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) existing;
                list.add(value);
            } else {
                List<Object> list = new ArrayList<>();
                list.add(existing);
                list.add(value);
                map.put(name, list);
            }
        }
        return map;
    }

    /** 取直接子元素，跳过文本、注释等非元素节点 */
    private static List<Element> childElements(Element element) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                out.add((Element) node);
            }
        }
        return out;
    }

    /**
     * 递归写出元素：集合展开为同名重复元素，对象写子元素，字符串按 cdata 包裹或转义，其他标量转义输出。
     *
     * @param cdata 字符串值是否包 CDATA
     */
    private static void appendElement(StringBuilder out, String name, Object value, boolean cdata) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("xmlStringify 节点名不能为空");
        }
        if (value instanceof Collection<?> list) {
            for (Object item : list) {
                appendElement(out, name, item, cdata);
            }
            return;
        }
        out.append('<').append(name).append('>');
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                appendElement(out, String.valueOf(entry.getKey()), entry.getValue(), cdata);
            }
        } else if (value instanceof String text) {
            out.append(cdata ? wrapCdata(text) : escapeXml(text));
        } else if (value != null) {
            out.append(escapeXml(String.valueOf(value)));
        }
        out.append("</").append(name).append('>');
    }

    /** 包 CDATA；文本中的 ]]> 拆成两段，避免提前结束 */
    private static String wrapCdata(String text) {
        return "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>";
    }

    /** 转义 XML 特殊字符 &lt; &gt; &amp; 引号 */
    private static String escapeXml(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** 签名原文中的值转文本：null 保持 null，对象 / 数组转 JSON，其余取字符串 */
    private static String valueText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            return ScriptValueConverter.jsonStringify(value);
        }
        return String.valueOf(value);
    }
}
