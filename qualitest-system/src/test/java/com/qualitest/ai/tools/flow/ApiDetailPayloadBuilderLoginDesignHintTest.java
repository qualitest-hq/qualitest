package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.AuthProfileTestFixtures;
import com.qualitest.api.util.LoginExtractSuggestor;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ApiDetailPayloadBuilder 登录口 designHints 文案（按 isLoginLikeApi）。
 * 边界：有建议时写具体路径；无法确定时写「跑一次再改」；非登录类 path 不追加。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiDetailPayloadBuilderLoginDesignHintTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiDetailPayloadBuilderLoginDesignHintTest {

    /**
     * 前提：单端模板 /login 建议（schema 含 token）；hints 为空。
     * 期望：首条含 $.token → token（asset 写入）。
     */
    @Test
    @Order(1)
    @DisplayName("有凭证建议时写入具体抽取说明")
    void prepend_whenSuggestionKnown() {
        JSONObject schema = new JSONObject();
        schema.put("token", "string");
        String authJson = ProjectAuthConfigSupport.toJson(ProjectAuthConfigSupport.ruoyiBearerTemplate());
        LoginExtractSuggestor.Suggestion suggestion = LoginExtractSuggestor.suggest(
                authJson, "/login", schema);
        List<String> hints = new ArrayList<>();

        ApiDetailPayloadBuilder.prependLoginDesignHint(hints, authJson, "/login", suggestion);

        assertEquals(1, hints.size());
        assertTrue(hints.get(0).contains("$.token"));
        assertTrue(hints.get(0).contains("token"));
    }

    /**
     * 前提：登录类 path；建议为 null（Map/未知路径）。
     * 期望：写入「跑一次后按真实 body 再改」。
     */
    @Test
    @Order(2)
    @DisplayName("无法确定路径时提示跑流后再改")
    void prepend_whenExprUnknown() {
        List<String> hints = new ArrayList<>();

        ApiDetailPayloadBuilder.prependLoginDesignHint(
                hints, AuthProfileTestFixtures.adminThenClientJson(), "/login", null);

        assertEquals(List.of(ApiDetailPayloadBuilder.LOGIN_EXTRACT_HINT_UNKNOWN), hints);
    }

    /**
     * 前提：非 isLoginLikeApi 的 path。
     * 期望：不追加 hint。
     */
    @Test
    @Order(3)
    @DisplayName("非登录类 path 不追加抽取 hint")
    void prepend_skipsNonLoginLike() {
        List<String> hints = new ArrayList<>();

        ApiDetailPayloadBuilder.prependLoginDesignHint(
                hints, AuthProfileTestFixtures.adminThenClientJson(), "/system/user/list", null);

        assertTrue(hints.isEmpty());
    }
}
