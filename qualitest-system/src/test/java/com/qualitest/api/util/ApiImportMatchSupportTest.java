package com.qualitest.api.util;

import com.qualitest.api.params.ApiImportParams;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ApiImportMatchSupport：导入时用「HTTP 方法 + apiPath」识别接口，并从 requestConfig 解析 method。
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
    void extractHttpMethod_defaultsToGet() {
        begin("extractHttpMethod_defaultsToGet");
        assertEquals("GET", ApiImportMatchSupport.extractHttpMethod(null));
        assertEquals("POST", ApiImportMatchSupport.extractHttpMethod("{\"method\":\"post\"}"));
        log("null=GET jsonPost=POST");
        end("extractHttpMethod_defaultsToGet");
    }

    /**
     * 前提：库中已有 GET /api/cart/my，导入项路径相同但方法不同或相同。
     * 期望：POST 不匹配；GET 匹配。
     */
    @Test
    @Order(2)
    void matches_distinguishesSamePathDifferentMethod() {
        begin("matches_distinguishesSamePathDifferentMethod");
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
        log("GET vs POST=false; GET vs GET=true");
        end("matches_distinguishesSamePathDifferentMethod");
    }

    /**
     * 前提：导入项 apiPath=/api/foo，requestConfig method=PUT。
     * 期望：buildIdentity 返回 "PUT /api/foo"。
     */
    @Test
    @Order(3)
    void buildIdentity_includesMethod() {
        begin("buildIdentity_includesMethod");
        ApiImportParams.ApiImportItem item = ApiImportParams.ApiImportItem.builder()
                .apiPath("/api/foo")
                .requestConfig("{\"method\":\"PUT\"}")
                .build();
        assertEquals("PUT /api/foo", ApiImportMatchSupport.buildIdentity(item));
        log("identity=PUT /api/foo");
        end("buildIdentity_includesMethod");
    }

    /**
     * 前提：TestProjectApi 含 apiPath 与 requestConfig method=DELETE。
     * 期望：buildIdentity 返回 "DELETE /api/foo"。
     */
    @Test
    @Order(4)
    void buildIdentity_fromTestProjectApi() {
        begin("buildIdentity_fromTestProjectApi");
        TestProjectApi api = TestProjectApi.builder()
                .apiPath("/api/foo")
                .requestConfig("{\"method\":\"DELETE\"}")
                .build();
        assertEquals("DELETE /api/foo", ApiImportMatchSupport.buildIdentity(api));
        log("apiIdentity=DELETE /api/foo");
        end("buildIdentity_fromTestProjectApi");
    }
}
