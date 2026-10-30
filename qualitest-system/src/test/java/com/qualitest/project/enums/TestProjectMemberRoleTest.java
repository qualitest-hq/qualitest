package com.qualitest.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：TestProjectMemberRole 角色 code 规范化与身份判定。
 * 边界：developer→backend、owner/admin/sysAdmin、自定义 code、null。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectMemberRoleTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectMemberRoleTest {

    /**
     * 前提：历史角色 code 为 developer。
     * 期望：normalizeCode 映射为 backend。
     */
    @Test
    @Order(1)
    @DisplayName("normalizeCode 将 developer 规范为 backend")
    void normalizeCode_developerToBackend() {
        assertEquals(TestProjectMemberRole.BACKEND.getCode(),
                TestProjectMemberRole.normalizeCode("developer"));
        assertEquals(TestProjectMemberRole.BACKEND,
                TestProjectMemberRole.getByCodeOrNull("developer"));
    }

    /**
     * 前提：种子角色与未知自定义 code。
     * 期望：已知原样返回；未知自定义原样保留；空值原样。
     */
    @Test
    @Order(2)
    @DisplayName("normalizeCode 保留已知与自定义 code")
    void normalizeCode_keepsKnownAndCustom() {
        assertEquals("owner", TestProjectMemberRole.normalizeCode("owner"));
        assertEquals("backend", TestProjectMemberRole.normalizeCode("backend"));
        assertEquals("frontend", TestProjectMemberRole.normalizeCode("frontend"));
        assertEquals("custom_role", TestProjectMemberRole.normalizeCode("custom_role"));
        assertNull(TestProjectMemberRole.normalizeCode(null));
        assertEquals("", TestProjectMemberRole.normalizeCode(""));
    }

    /**
     * 前提：所有者与管理员角色 code。
     * 期望：isOwner / isAdmin 为 true。
     */
    @Test
    @Order(3)
    @DisplayName("isOwner 与 isAdmin 识别种子角色")
    void isOwnerAndAdmin() {
        assertTrue(TestProjectMemberRole.isOwner(TestProjectMemberRole.OWNER.getCode()));
        assertTrue(TestProjectMemberRole.isAdmin(TestProjectMemberRole.ADMIN.getCode()));
        assertFalse(TestProjectMemberRole.isOwner(TestProjectMemberRole.ADMIN.getCode()));
        assertFalse(TestProjectMemberRole.isAdmin(TestProjectMemberRole.OWNER.getCode()));
        assertFalse(TestProjectMemberRole.isOwner(TestProjectMemberRole.BACKEND.getCode()));
        assertFalse(TestProjectMemberRole.isAdmin(TestProjectMemberRole.FRONTEND.getCode()));
    }

    /**
     * 前提：虚拟超管 sysAdmin。
     * 期望：isSysAdmin 为 true；其它种子角色为 false。
     */
    @Test
    @Order(4)
    @DisplayName("isSysAdmin 识别虚拟超管")
    void isSysAdmin() {
        assertTrue(TestProjectMemberRole.isSysAdmin(TestProjectMemberRole.SYS_ADMIN.getCode()));
        assertFalse(TestProjectMemberRole.isSysAdmin(TestProjectMemberRole.OWNER.getCode()));
        assertFalse(TestProjectMemberRole.isSysAdmin(null));
    }

    /**
     * 前提：backend / frontend 种子角色存在。
     * 期望：getByCodeOrNull 可解析。
     */
    @Test
    @Order(5)
    @DisplayName("BACKEND 与 FRONTEND 可按 code 解析")
    void backendAndFrontendResolvable() {
        assertEquals(TestProjectMemberRole.BACKEND,
                TestProjectMemberRole.getByCodeOrNull(TestProjectMemberRole.BACKEND.getCode()));
        assertEquals(TestProjectMemberRole.FRONTEND,
                TestProjectMemberRole.getByCodeOrNull(TestProjectMemberRole.FRONTEND.getCode()));
        assertNull(TestProjectMemberRole.getByCodeOrNull("unknown_custom"));
    }
}
