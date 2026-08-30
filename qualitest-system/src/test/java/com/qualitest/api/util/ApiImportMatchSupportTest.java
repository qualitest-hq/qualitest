package com.qualitest.api.util;

import com.qualitest.api.params.ApiImportParams;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ApiImportMatchSupport：导入时用「HTTP 方法 + apiPath」识别接口，并从 requestConfig 解析 method；上传保护判定。
 * 边界：纯函数，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiImportMatchSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiImportMatchSupportTest {

    /**
     * 前提：requestConfig 为 null 或 JSON 含 method 字段。
     * 期望：分别解析为 GET、POST 等大写 HTTP 方法名。
     */
    @Test
    @Order(1)
    @DisplayName("解析 method：null 默认 GET，JSON 取大写方法名")
    void extractHttpMethod_defaultsToGet() {
        assertEquals("GET", ApiImportMatchSupport.extractHttpMethod(null));
        assertEquals("POST", ApiImportMatchSupport.extractHttpMethod("{\"method\":\"post\"}"));
    }

    /**
     * 前提：库中已有 GET /api/cart/my，导入项路径相同但方法不同或相同。
     * 期望：POST 不匹配；GET 匹配。
     */
    @Test
    @Order(2)
    @DisplayName("匹配：同路径不同 method 不命中，同 method 命中")
    void matches_distinguishesSamePathDifferentMethod() {
        TestProjectApi existing = TestProjectApi.builder()
                .apiPath("/api/cart/my")
                .requestConfig("{\"method\":\"GET\"}")
                .build();
        ApiImportParams.ApiImportItem postItem = ApiImportParams.ApiImportItem.builder()
                .apiPath("/api/cart/my")
                .requestConfig("{\"method\":\"POST\"}")
                .build();

        assertFalse(ApiImportMatchSupport.matches(existing, postItem));

        ApiImportParams.ApiImportItem getItem = ApiImportParams.ApiImportItem.builder()
                .apiPath("/api/cart/my")
                .requestConfig("{\"method\":\"GET\"}")
                .build();
        assertTrue(ApiImportMatchSupport.matches(existing, getItem));
    }

    /**
     * 前提：导入项 apiPath=/api/foo，requestConfig method=PUT。
     * 期望：buildIdentity 返回 "PUT /api/foo"。
     */
    @Test
    @Order(3)
    @DisplayName("身份：导入项含 method 与 path")
    void buildIdentity_includesMethod() {
        ApiImportParams.ApiImportItem item = ApiImportParams.ApiImportItem.builder()
                .apiPath("/api/foo")
                .requestConfig("{\"method\":\"PUT\"}")
                .build();
        assertEquals("PUT /api/foo", ApiImportMatchSupport.buildIdentity(item));
    }

    /**
     * 前提：TestProjectApi 含 apiPath 与 requestConfig method=DELETE。
     * 期望：buildIdentity 返回 "DELETE /api/foo"。
     */
    @Test
    @Order(4)
    @DisplayName("身份：从 TestProjectApi 拼 method + path")
    void buildIdentity_fromTestProjectApi() {
        TestProjectApi api = TestProjectApi.builder()
                .apiPath("/api/foo")
                .requestConfig("{\"method\":\"DELETE\"}")
                .build();
        assertEquals("DELETE /api/foo", ApiImportMatchSupport.buildIdentity(api));
    }

    /**
     * 前提：syncProtected 为 1 / 0 / null。
     * 期望：仅 1 视为开启上传保护。
     */
    @Test
    @Order(5)
    @DisplayName("上传保护：仅 syncProtected=1 为真")
    void isSyncProtected_onlyWhenOne() {
        assertFalse(ApiImportMatchSupport.isSyncProtected(null));
        assertFalse(ApiImportMatchSupport.isSyncProtected(TestProjectApi.builder().syncProtected(0).build()));
        assertFalse(ApiImportMatchSupport.isSyncProtected(TestProjectApi.builder().syncProtected(null).build()));
        assertTrue(ApiImportMatchSupport.isSyncProtected(TestProjectApi.builder().syncProtected(1).build()));
    }
}
