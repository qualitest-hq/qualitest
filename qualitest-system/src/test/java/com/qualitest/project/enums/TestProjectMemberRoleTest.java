package com.qualitest.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TestProjectMemberRole：成员管理 / Token 权限判定。
 * <p>
 * 边界：sysAdmin 虚拟角色、owner/admin、developer/tester、null。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectMemberRoleTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectMemberRoleTest {

    /**
     * 前提：超级管理员经 getCheckProjectMemberRole 得到 sysAdmin。
     * 期望：可管理项目成员，避免接口报「您无权管理项目成员」。
     */
    @Test
    @Order(1)
    @DisplayName("sysAdmin 可管理项目成员")
    void canManageMember_sysAdmin() {
        assertTrue(TestProjectMemberRole.canManageMember(TestProjectMemberRole.SYS_ADMIN.getCode()));
    }

    /**
     * 前提：项目所有者与项目管理员。
     * 期望：均可管理成员。
     */
    @Test
    @Order(2)
    @DisplayName("owner 与 admin 可管理项目成员")
    void canManageMember_ownerAndAdmin() {
        assertTrue(TestProjectMemberRole.canManageMember(TestProjectMemberRole.OWNER.getCode()));
        assertTrue(TestProjectMemberRole.canManageMember(TestProjectMemberRole.ADMIN.getCode()));
    }

    /**
     * 前提：开发者、测试员或空角色。
     * 期望：不可管理成员。
     */
    @Test
    @Order(3)
    @DisplayName("developer、tester、null 不可管理项目成员")
    void canManageMember_deniedRoles() {
        assertFalse(TestProjectMemberRole.canManageMember(TestProjectMemberRole.DEVELOPER.getCode()));
        assertFalse(TestProjectMemberRole.canManageMember(TestProjectMemberRole.TESTER.getCode()));
        assertFalse(TestProjectMemberRole.canManageMember(null));
    }

    /**
     * 前提：超级管理员刷新项目 Token。
     * 期望：sysAdmin 与 owner/admin/developer 均可使用 Token。
     */
    @Test
    @Order(4)
    @DisplayName("sysAdmin 可使用项目 Token")
    void canUseProjectToken_sysAdminAndAllowedRoles() {
        assertTrue(TestProjectMemberRole.canUseProjectToken(TestProjectMemberRole.SYS_ADMIN.getCode()));
        assertTrue(TestProjectMemberRole.canUseProjectToken(TestProjectMemberRole.OWNER.getCode()));
        assertTrue(TestProjectMemberRole.canUseProjectToken(TestProjectMemberRole.ADMIN.getCode()));
        assertTrue(TestProjectMemberRole.canUseProjectToken(TestProjectMemberRole.DEVELOPER.getCode()));
        assertFalse(TestProjectMemberRole.canUseProjectToken(TestProjectMemberRole.TESTER.getCode()));
        assertFalse(TestProjectMemberRole.canUseProjectToken(null));
    }
}
