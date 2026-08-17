package com.qualitest.ai.tools.flow;

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
 * 测 ApiDetailPayloadBuilder 登录口 designHints 文案。
 * 边界：有 hint 时写具体路径；无法确定时写「跑一次再改」。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiDetailPayloadBuilderLoginHintTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiDetailPayloadBuilderLoginHintTest {

    /**
     * 前提：双端模板管理端建议；hints 为空。
     * 期望：首条为 $.token → adminToken。
     */
    @Test
    @Order(1)
    @DisplayName("有 loginHint 时写入具体抽取说明")
    void prepend_whenHintKnown() {
        LoginExtractSuggestor.Suggestion suggestion = LoginExtractSuggestor.suggest(
                ProjectAuthConfigSupport.toJson(ProjectAuthConfigSupport.dualBearerTemplate()),
                "/login",
                null);
        List<String> hints = new ArrayList<>();

        ApiDetailPayloadBuilder.prependLoginDesignHint(hints, "/login", suggestion);

        assertEquals(1, hints.size());
        assertTrue(hints.get(0).contains("$.token"));
        assertTrue(hints.get(0).contains("adminToken"));
    }

    /**
     * 前提：登录 path；建议为 null（Map/未知路径）。
     * 期望：写入「跑一次后按真实 body 再改」。
     */
    @Test
    @Order(2)
    @DisplayName("无法确定路径时提示跑流后再改")
    void prepend_whenExprUnknown() {
        List<String> hints = new ArrayList<>();

        ApiDetailPayloadBuilder.prependLoginDesignHint(hints, "/login", null);

        assertEquals(List.of(ApiDetailPayloadBuilder.LOGIN_EXTRACT_HINT_UNKNOWN), hints);
    }

    /**
     * 前提：非登录 path。
     * 期望：不追加 hint。
     */
    @Test
    @Order(3)
    @DisplayName("非登录口不追加抽取 hint")
    void prepend_skipsNonLogin() {
        List<String> hints = new ArrayList<>();

        ApiDetailPayloadBuilder.prependLoginDesignHint(hints, "/system/user/list", null);

        assertTrue(hints.isEmpty());
    }
}
