package com.qualitest.flow.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 HttpStepDetailsDesensitizer：外联步骤报告中 XML 原文 bodyText 的敏感标签脱敏。
 * 边界：纯函数；只覆盖 response.bodyText，其余字段规则不在此测。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpStepDetailsDesensitizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpStepDetailsDesensitizerTest {

    /**
     * 前提：response.bodyText 为 XML，含 access_token（CDATA）、带属性的 password 与普通字段。
     * 期望：敏感标签内容替换为 ***，标签与属性保留；普通字段原样。
     */
    @Test
    @Order(1)
    @DisplayName("XML 原文中敏感标签内容被脱敏")
    void desensitize_xmlBodyText_masksSensitiveTags() {
        String xml = "<xml><access_token><![CDATA[abc123]]></access_token>"
                + "<password type=\"plain\">p@ss</password>"
                + "<return_code><![CDATA[SUCCESS]]></return_code></xml>";
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bodyText", xml);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("response", response);

        Map<String, Object> out = HttpStepDetailsDesensitizer.desensitize(details);
        String masked = (String) ((Map<?, ?>) out.get("response")).get("bodyText");

        assertTrue(masked.contains("<access_token>***</access_token>"));
        assertTrue(masked.contains("<password type=\"plain\">***</password>"));
        assertTrue(masked.contains("<return_code><![CDATA[SUCCESS]]></return_code>"));
        assertFalse(masked.contains("abc123"));
        assertFalse(masked.contains("p@ss"));
    }
}
