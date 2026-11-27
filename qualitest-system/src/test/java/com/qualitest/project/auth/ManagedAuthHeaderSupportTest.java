package com.qualitest.project.auth;

import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.project.result.ManagedAuthHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测详情接口附带的托管鉴权头。
 * 覆盖：inherit 命中客户端 Profile、override 自定义头、mode=none 返回 null、
 * 预制列表标 none 时 inherit 接口仍返回托管头。
 * 单跑：{@code mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ManagedAuthHeaderSupportTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ManagedAuthHeaderSupportTest {

    private static final String PROJECT_AUTH = ProjectAuthConfigSupport.toJson(
            AuthProfileTestFixtures.adminThenClient());

    /**
     * 前提：inherit，路径命中客户端 Profile。
     * 期望：source=profile，头模板为客户端 Bearer，并带上配置名称。
     */
    @Test
    @Order(1)
    @DisplayName("inherit 返回客户端托管头")
    void resolve_inherit_clientProfile() {
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("inherit").build());
        ManagedAuthHeader header = ManagedAuthHeaderSupport.resolve(
                apiAuth, PROJECT_AUTH, "/api/coupon/coupon/available");
        assertEquals("Authorization", header.getName());
        assertEquals("Bearer {{asset.clientAuth.token}}", header.getValueTemplate());
        assertEquals(ProjectAuthConfigSupport.PROFILE_CLIENT, header.getAuthProfileId());
        assertEquals("客户端 Bearer", header.getProfileName());
        assertEquals(ManagedAuthHeaderSupport.SOURCE_PROFILE, header.getSource());
    }

    /**
     * 前提：mode=override，自定义头为字面量。
     * 期望：source=override，无 profileId 与名称。
     */
    @Test
    @Order(2)
    @DisplayName("override 返回接口自定义头")
    void resolve_override_customHeader() {
        String apiAuth = ApiAuthConfigSupport.toStorageJson(ApiAuthConfig.builder()
                .mode("override")
                .header(ApiAuthConfig.Header.builder()
                        .name("X-Token")
                        .valueTemplate("{{asset.clientAuth.token}}")
                        .build())
                .build());
        ManagedAuthHeader header = ManagedAuthHeaderSupport.resolve(
                apiAuth, PROJECT_AUTH, "/api/orders");
        assertEquals("X-Token", header.getName());
        assertEquals("{{asset.clientAuth.token}}", header.getValueTemplate());
        assertNull(header.getAuthProfileId());
        assertNull(header.getProfileName());
        assertEquals(ManagedAuthHeaderSupport.SOURCE_OVERRIDE, header.getSource());
    }

    /**
     * 前提：mode=none。
     * 期望：不附带托管头。
     */
    @Test
    @Order(3)
    @DisplayName("none 不返回托管头")
    void resolve_none_null() {
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("none").build());
        assertNull(ManagedAuthHeaderSupport.resolve(apiAuth, PROJECT_AUTH, "/api/orders"));
    }

    /**
     * 前提：预制列表把 /login 标成 none，接口 auth.mode=inherit，且 pathPrefix 命中。
     * 期望：仍返回托管头；免登只看接口 mode。
     */
    @Test
    @Order(4)
    @DisplayName("预制 none 不旁路 inherit")
    void resolve_prefabricatedNone_stillReturnsHeader() {
        String projectAuth = """
                {"authProfiles":[{
                  "id":"defaultBearer","name":"Bearer",
                  "match":{"pathPrefix":["/login"]},
                  "headerName":"Authorization","headerValueTemplate":"Bearer {{asset.adminAuth.token}}",
                  "apis":[{"apiPath":"/login","authConfig":{"mode":"none"},
                    "requestConfig":{"configVersion":1,"method":"POST"}}]
                }]}
                """;
        String apiAuth = ApiAuthConfigSupport.toStorageJson(
                ApiAuthConfig.builder().mode("inherit").build());
        ManagedAuthHeader header = ManagedAuthHeaderSupport.resolve(
                apiAuth, projectAuth, "/login");
        assertEquals("Authorization", header.getName());
        assertEquals("Bearer {{asset.adminAuth.token}}", header.getValueTemplate());
    }
}
