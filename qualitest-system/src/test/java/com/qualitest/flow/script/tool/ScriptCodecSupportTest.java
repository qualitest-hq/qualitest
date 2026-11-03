package com.qualitest.flow.script.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ScriptCodecSupport：URL 编解码、XML 解析与生成、签名原文拼接。
 * 边界：纯 Java，无 Spring。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ScriptCodecSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScriptCodecSupportTest {

    /**
     * 前提：含空格、中文、&= 的文本。
     * 期望：空格编码为 +，解码还原。
     */
    @Test
    @Order(1)
    @DisplayName("URL 编解码往返")
    void url_roundTrip() {
        String encoded = ScriptCodecSupport.urlEncode("a b&c=中");
        assertEquals("a+b%26c%3D%E4%B8%AD", encoded);
        assertEquals("a b&c=中", ScriptCodecSupport.urlDecode(encoded));
    }

    /**
     * 前提：微信 v2 风格 XML，含 CDATA、数字节点、同名重复节点与嵌套节点。
     * 期望：去掉根节点；CDATA 取文本；重复节点转数组；嵌套转对象。
     */
    @Test
    @Order(2)
    @DisplayName("XML 解析 CDATA / 重复 / 嵌套")
    @SuppressWarnings("unchecked")
    void xmlParse_wechatStyle() {
        String xml = """
                <xml>
                  <return_code><![CDATA[SUCCESS]]></return_code>
                  <total_fee>100</total_fee>
                  <item>a</item>
                  <item>b</item>
                  <detail><sku>S1</sku></detail>
                </xml>
                """;
        Map<String, Object> parsed = (Map<String, Object>) ScriptCodecSupport.xmlParse(xml);
        assertEquals("SUCCESS", parsed.get("return_code"));
        assertEquals("100", parsed.get("total_fee"));
        assertEquals(List.of("a", "b"), parsed.get("item"));
        assertEquals(Map.of("sku", "S1"), parsed.get("detail"));
    }

    /**
     * 前提：XML 含 DOCTYPE 外部实体声明。
     * 期望：直接报错，不解析实体。
     */
    @Test
    @Order(3)
    @DisplayName("XML 拒绝 DOCTYPE 防 XXE")
    void xmlParse_rejectsDoctype() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><xml><a>&e;</a></xml>";
        assertThrows(IllegalArgumentException.class, () -> ScriptCodecSupport.xmlParse(xml));
    }

    /**
     * 前提：对象含字符串、数字、数组、嵌套对象与含 ]]> 的文本，默认 root=xml、cdata=true。
     * 期望：字符串包 CDATA，数字不包；数组展开为重复节点；再解析得到同样的值。
     */
    @Test
    @Order(4)
    @DisplayName("XML 生成并可往返解析")
    @SuppressWarnings("unchecked")
    void xmlStringify_roundTrip() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("return_code", "SUCCESS");
        body.put("total_fee", 100L);
        body.put("item", List.of("a", "b"));
        body.put("note", "x]]>y");
        String xml = ScriptCodecSupport.xmlStringify(body, null);
        assertTrue(xml.startsWith("<xml><return_code><![CDATA[SUCCESS]]></return_code><total_fee>100</total_fee>"));
        assertTrue(xml.contains("<item><![CDATA[a]]></item><item><![CDATA[b]]></item>"));

        Map<String, Object> parsed = (Map<String, Object>) ScriptCodecSupport.xmlParse(xml);
        assertEquals("x]]>y", parsed.get("note"));
        assertEquals(List.of("a", "b"), parsed.get("item"));
    }

    /**
     * 前提：cdata=false、root=req，文本含 & 与 <。
     * 期望：使用 req 根节点，特殊字符转义。
     */
    @Test
    @Order(5)
    @DisplayName("XML 生成关闭 CDATA 时转义")
    void xmlStringify_escapesWithoutCdata() {
        String xml = ScriptCodecSupport.xmlStringify(Map.of("q", "a&b<c"), Map.of("root", "req", "cdata", false));
        assertEquals("<req><q>a&amp;b&lt;c</q></req>", xml);
    }

    /**
     * 前提：乱序 key，含 sign、空串、null、数字与嵌套对象。
     * 期望：ASCII 升序；默认排除 sign 并跳过空值；对象序列化为 JSON。
     */
    @Test
    @Order(6)
    @DisplayName("签名原文排序、排除与跳过空值")
    void signContent_defaults() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("b", 2L);
        params.put("sign", "xxx");
        params.put("a", "1");
        params.put("empty", "");
        params.put("nil", null);
        params.put("biz", Map.of("k", "v"));
        assertEquals("a=1&b=2&biz={\"k\":\"v\"}", ScriptCodecSupport.signContent(params, null));
    }

    /**
     * 前提：exclude=[sign, sign_type]，skipEmpty=false。
     * 期望：两个 key 均排除；空串保留为 k=。
     */
    @Test
    @Order(7)
    @DisplayName("签名原文自定义排除与保留空值")
    void signContent_customOptions() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sign_type", "RSA2");
        params.put("sign", "xxx");
        params.put("app_id", "2021");
        params.put("empty", "");
        Map<String, Object> opts = Map.of("exclude", List.of("sign", "sign_type"), "skipEmpty", false);
        assertEquals("app_id=2021&empty=", ScriptCodecSupport.signContent(params, opts));
    }
}
