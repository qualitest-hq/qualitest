package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.params.ApiImportParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 McpApiImportItemMapper：把结构化 items 转成导入参数包。
 * 覆盖：path 规范化、请求头写入声明头、鉴权建议、缺必填失败。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpApiImportItemMapperTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpApiImportItemMapperTest {

    /**
     * 前提：path 无前导 /、带尾 /；含 headerParams 与 authSuggestion。
     * 期望：path 规范化；requestConfig 含 declaredHeaders；auth.mode=none；sourceSystem=mcp-agent。
     */
    @Test
    @Order(1)
    @DisplayName("映射：path 规范化、headerParams 与 auth")
    void toImportParams_normalizesPathAndMapsHeadersAuth() {
        JSONObject item = new JSONObject();
        item.put("method", "post");
        item.put("path", "api/user/login/");
        item.put("name", "登录");
        item.put("apiGroup", "用户.登录");
        item.put("description", "用户登录");
        JSONArray headers = new JSONArray();
        JSONObject h = new JSONObject();
        h.put("name", "X-Request-Id");
        h.put("type", "string");
        headers.add(h);
        item.put("headerParams", headers);
        JSONObject auth = new JSONObject();
        auth.put("mode", "none");
        item.put("authSuggestion", auth);

        McpApiImportItemMapper.MappedBatch batch =
                McpApiImportItemMapper.toImportParams(List.of(item));

        assertEquals(1, batch.params().getApiList().size());
        ApiImportParams.ApiImportItem api = batch.params().getApiList().get(0);
        assertEquals("/api/user/login", api.getApiPath());
        assertEquals("登录", api.getApiName());
        assertEquals("用户.登录", api.getApiGroup());
        assertEquals(McpApiImportItemMapper.SOURCE_SYSTEM, api.getSourceSystem());
        assertNotNull(api.getAuth());
        assertEquals("none", api.getAuth().getMode());
        assertTrue(api.getRequestConfig().contains("\"method\":\"POST\""));
        assertTrue(api.getRequestConfig().contains("declaredHeaders"));
        assertTrue(api.getRequestConfig().contains("X-Request-Id"));

        McpApiImportItemMapper.MetaFlags flags = batch.metaFlags().get(0);
        assertTrue(flags.groupProvided());
        assertTrue(flags.descriptionProvided());
        assertEquals("POST", flags.method());
        assertEquals("/api/user/login", flags.normalizedPath());
    }

    /**
     * 前提：仅 method/path/name，无分组注释。
     * 期望：metaFlags 标记未提供；apiGroup/description 为空。
     */
    @Test
    @Order(2)
    @DisplayName("映射：省略分组注释时 flags 为未提供")
    void toImportParams_omittedMeta_flagsFalse() {
        Map<String, Object> item = Map.of(
                "method", "GET",
                "path", "/api/ping",
                "name", "ping");

        McpApiImportItemMapper.MappedBatch batch =
                McpApiImportItemMapper.toImportParams(List.of(item));

        ApiImportParams.ApiImportItem api = batch.params().getApiList().get(0);
        assertEquals("/api/ping", api.getApiPath());
        assertFalse(batch.metaFlags().get(0).groupProvided());
        assertFalse(batch.metaFlags().get(0).descriptionProvided());
    }

    /**
     * 前提：缺少 name。
     * 期望：抛 ServiceException。
     */
    @Test
    @Order(3)
    @DisplayName("映射：缺 name 失败")
    void toImportParams_missingName_throws() {
        JSONObject item = new JSONObject();
        item.put("method", "GET");
        item.put("path", "/x");
        assertThrows(com.qualitest.common.exception.ServiceException.class,
                () -> McpApiImportItemMapper.toImportParams(List.of(item)));
    }
}
