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
 * ApiImportMatchSupport 单元测试。
 * <p>
 * 验证导入时如何用「HTTP 方法 + apiPath」识别同一条接口，以及如何从 requestConfig 解析 method。
 * <p>
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ApiImportMatchSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiImportMatchSupportTest {

    /** 缺省或 JSON 中带 method 时，应解析出正确的 HTTP 方法名（大写）。 */
    @Test
    @Order(1)
    void extractHttpMethod_defaultsToGet() {
        begin("extractHttpMethod_defaultsToGet");
        assertEquals("GET", ApiImportMatchSupport.extractHttpMethod(null));
        assertEquals("POST", ApiImportMatchSupport.extractHttpMethod("{\"method\":\"post\"}"));
        log("null=GET jsonPost=POST");
        end("extractHttpMethod_defaultsToGet");
    }

    /** 路径相同但方法不同时不应匹配；路径与方法都相同时应匹配。 */
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

    /** 唯一键应由大写方法名与路径拼接，例如 PUT /api/foo。 */
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

    /** 库中 TestProjectApi 记录也能生成与导入项相同格式的唯一键。 */
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
