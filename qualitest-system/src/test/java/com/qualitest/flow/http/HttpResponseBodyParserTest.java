package com.qualitest.flow.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 HttpResponseBodyParser：响应正文按 JSON / XML 解析为对象，其余保留原文。
 * 边界：纯函数；XML 去根节点、同名节点转数组、忽略属性；解析失败不抛异常。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpResponseBodyParserTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpResponseBodyParserTest {

    /**
     * 前提：Content-Type=application/xml，正文含声明、根节点与重复子节点。
     * 期望：kind=xml；去掉根节点后可直接取字段，同名节点转为数组。
     */
    @Test
    @Order(1)
    @DisplayName("Content-Type 为 xml 时解析为对象")
    void parse_xmlContentType_returnsObject() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<response><code>0</code><item>a</item><item>b</item></response>";
        HttpResponseBodyParser.Parsed parsed = HttpResponseBodyParser.parse(
                xml, Map.of("Content-Type", "application/xml; charset=UTF-8"));
        assertEquals("xml", parsed.kind());
        assertTrue(parsed.isXml());
        Map<?, ?> body = assertInstanceOf(Map.class, parsed.body());
        assertEquals("0", body.get("code"));
        assertEquals(List.of("a", "b"), body.get("item"));
    }

    /**
     * 前提：微信支付 v2 风格，Content-Type=text/plain，正文以 &lt;xml&gt; 开头且字段包 CDATA。
     * 期望：kind=xml；CDATA 文本被取出。
     */
    @Test
    @Order(2)
    @DisplayName("text/plain 的 <xml> 报文按 XML 解析")
    void parse_textPlainWechatXml_returnsObject() {
        String xml = "<xml><return_code><![CDATA[SUCCESS]]></return_code>"
                + "<return_msg><![CDATA[OK]]></return_msg></xml>";
        HttpResponseBodyParser.Parsed parsed = HttpResponseBodyParser.parse(
                xml, Map.of("Content-Type", "text/plain"));
        assertEquals("xml", parsed.kind());
        Map<?, ?> body = assertInstanceOf(Map.class, parsed.body());
        assertEquals("SUCCESS", body.get("return_code"));
        assertEquals("OK", body.get("return_msg"));
    }

    /**
     * 前提：无响应头，正文以 BOM 与空白开头的 &lt;?xml 报文。
     * 期望：kind=xml。
     */
    @Test
    @Order(3)
    @DisplayName("无 Content-Type 时嗅探 <?xml 开头")
    void parse_noContentTypeWithBom_sniffsXml() {
        String xml = "\uFEFF  <?xml version=\"1.0\"?><r><a>1</a></r>";
        HttpResponseBodyParser.Parsed parsed = HttpResponseBodyParser.parse(xml, null);
        assertEquals("xml", parsed.kind());
        assertEquals("1", ((Map<?, ?>) parsed.body()).get("a"));
    }

    /**
     * 前提：Content-Type=text/xml，但正文不是合法 XML。
     * 期望：kind=nonJson，body 保留原文。
     */
    @Test
    @Order(4)
    @DisplayName("非法 XML 回落原文")
    void parse_invalidXml_keepsRawText() {
        String raw = "<xml><a>1</xml>";
        HttpResponseBodyParser.Parsed parsed = HttpResponseBodyParser.parse(
                raw, Map.of("Content-Type", "text/xml"));
        assertEquals("nonJson", parsed.kind());
        assertEquals(raw, parsed.body());
    }

    /**
     * 前提：Content-Type=text/html 的 HTML 页面。
     * 期望：不按 XML 解析，kind=nonJson，body 为原文。
     */
    @Test
    @Order(5)
    @DisplayName("HTML 不按 XML 解析")
    void parse_html_keepsRawText() {
        String html = "<html><body><p>login</p></body></html>";
        HttpResponseBodyParser.Parsed parsed = HttpResponseBodyParser.parse(
                html, Map.of("Content-Type", "text/html"));
        assertEquals("nonJson", parsed.kind());
        assertEquals(html, parsed.body());
    }

    /**
     * 前提：Content-Type=application/xml，正文带 DOCTYPE 声明外部实体。
     * 期望：拒绝解析，kind=nonJson，body 为原文。
     */
    @Test
    @Order(6)
    @DisplayName("含 DOCTYPE 的 XML 拒绝解析")
    void parse_doctype_rejected() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r><a>&x;</a></r>";
        HttpResponseBodyParser.Parsed parsed = HttpResponseBodyParser.parse(
                xml, Map.of("Content-Type", "application/xml"));
        assertEquals("nonJson", parsed.kind());
        assertEquals(xml, parsed.body());
    }

    /**
     * 前提：JSON 对象正文，以及以 { 开头但非法的正文。
     * 期望：合法 JSON → kind=json 且为对象；非法 → kind=nonJson 且保留原文。
     */
    @Test
    @Order(7)
    @DisplayName("JSON 解析行为不变")
    void parse_json_unchanged() {
        HttpResponseBodyParser.Parsed ok = HttpResponseBodyParser.parse(
                "{\"code\":0}", Map.of("Content-Type", "application/json"));
        assertEquals("json", ok.kind());
        assertEquals(0, ((Map<?, ?>) ok.body()).get("code"));

        HttpResponseBodyParser.Parsed bad = HttpResponseBodyParser.parse("{bad", null);
        assertEquals("nonJson", bad.kind());
        assertEquals("{bad", bad.body());
    }

    /**
     * 前提：空白正文；以及 Content-Type=application/json 却以 &lt;xml&gt; 开头的正文。
     * 期望：空白 → body=null、kind=nonJson；声明 json 的 XML 不嗅探，保留原文。
     */
    @Test
    @Order(8)
    @DisplayName("空正文与非 xml/text 声明不解析")
    void parse_blankOrOtherContentType_notParsed() {
        HttpResponseBodyParser.Parsed blank = HttpResponseBodyParser.parse("  ", null);
        assertNull(blank.body());
        assertEquals("nonJson", blank.kind());

        String raw = "<xml><a>1</a></xml>";
        HttpResponseBodyParser.Parsed declaredJson = HttpResponseBodyParser.parse(
                raw, Map.of("content-type", "application/json"));
        assertEquals("nonJson", declaredJson.kind());
        assertEquals(raw, declaredJson.body());
    }
}
