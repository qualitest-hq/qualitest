package com.qualitest.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：默认种子角色权限矩阵期望集合（owner/admin/backend/frontend/tester）。
 * 边界：owner 全开；admin 无 delete/transfer；backend 无项目写/成员；frontend 查看+执行+token+mcp:read；tester 无 token/MCP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectMemberPermSeedMatrixTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectMemberPermSeedMatrixTest {

    private static Set<String> codesOf(TestProjectMemberPerm... perms) {
        return Arrays.stream(perms).map(TestProjectMemberPerm::getCode).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static Set<String> allCodes() {
        return new java.util.LinkedHashSet<>(TestProjectMemberPerm.allCodes());
    }

    /** admin 期望：全量去掉 project:delete / project:transfer */
    private static Set<String> adminSeed() {
        Set<String> set = allCodes();
        set.remove(TestProjectMemberPerm.PROJECT_DELETE.getCode());
        set.remove(TestProjectMemberPerm.PROJECT_TRANSFER.getCode());
        return set;
    }

    /** backend 期望权限集合 */
    private static Set<String> backendSeed() {
        Set<String> set = allCodes();
        set.remove(TestProjectMemberPerm.PROJECT_EDIT.getCode());
        set.remove(TestProjectMemberPerm.PROJECT_DELETE.getCode());
        set.remove(TestProjectMemberPerm.PROJECT_TRANSFER.getCode());
        set.remove(TestProjectMemberPerm.MEMBER_VIEW.getCode());
        set.remove(TestProjectMemberPerm.MEMBER_ADD.getCode());
        set.remove(TestProjectMemberPerm.MEMBER_EDIT.getCode());
        set.remove(TestProjectMemberPerm.MEMBER_REMOVE.getCode());
        return set;
    }

    /** frontend 期望权限集合 */
    private static Set<String> frontendSeed() {
        return codesOf(
                TestProjectMemberPerm.API_VIEW,
                TestProjectMemberPerm.FLOW_VIEW,
                TestProjectMemberPerm.FLOW_RUN,
                TestProjectMemberPerm.SUITE_VIEW,
                TestProjectMemberPerm.SUITE_RUN,
                TestProjectMemberPerm.ENV_VIEW,
                TestProjectMemberPerm.ASSET_VIEW,
                TestProjectMemberPerm.TOKEN_USE,
                TestProjectMemberPerm.MCP_READ
        );
    }

    /** tester 期望权限集合 */
    private static Set<String> testerSeed() {
        return codesOf(
                TestProjectMemberPerm.API_VIEW,
                TestProjectMemberPerm.FLOW_VIEW,
                TestProjectMemberPerm.FLOW_RUN,
                TestProjectMemberPerm.SUITE_VIEW,
                TestProjectMemberPerm.SUITE_RUN,
                TestProjectMemberPerm.ENV_VIEW,
                TestProjectMemberPerm.ASSET_VIEW
        );
    }

    /**
     * 前提：owner 角色应挂载目录内全部权限。
     * 期望：allCodes 覆盖 delete/transfer/MCP 跑流等关键 code。
     */
    @Test
    @Order(1)
    @DisplayName("owner 种子期望为全部权限")
    void owner_hasAllPerms() {
        assertEquals(EnumSet.allOf(TestProjectMemberPerm.class).size(), allCodes().size());
        assertTrue(allCodes().contains(TestProjectMemberPerm.PROJECT_DELETE.getCode()));
        assertTrue(allCodes().contains(TestProjectMemberPerm.PROJECT_TRANSFER.getCode()));
        assertTrue(allCodes().contains(TestProjectMemberPerm.MCP_RUN.getCode()));
    }

    /**
     * 前提：admin 期望集合由全量去掉 delete/transfer 得到。
     * 期望：不含 delete/transfer，仍含 edit 与 member:add，数量为全量减 2。
     */
    @Test
    @Order(2)
    @DisplayName("admin 种子无 project:delete/transfer，其余为全量权限")
    void admin_excludesDeleteAndTransfer() {
        Set<String> admin = adminSeed();
        assertFalse(admin.contains(TestProjectMemberPerm.PROJECT_DELETE.getCode()));
        assertFalse(admin.contains(TestProjectMemberPerm.PROJECT_TRANSFER.getCode()));
        assertTrue(admin.contains(TestProjectMemberPerm.PROJECT_EDIT.getCode()));
        assertTrue(admin.contains(TestProjectMemberPerm.MEMBER_ADD.getCode()));
        assertEquals(allCodes().size() - 2, admin.size());
    }

    /**
     * 前提：backend 期望集合去掉项目写与成员管理。
     * 期望：仍含 API/AI/MCP 写权限，不含 PROJECT_EDIT 与 MEMBER_VIEW。
     */
    @Test
    @Order(3)
    @DisplayName("backend 种子无项目写与成员权限，含 AI/插件/MCP")
    void backend_hasWriteWithoutMemberOrProjectWrite() {
        Set<String> backend = backendSeed();
        assertFalse(backend.contains(TestProjectMemberPerm.PROJECT_EDIT.getCode()));
        assertFalse(backend.contains(TestProjectMemberPerm.MEMBER_VIEW.getCode()));
        assertTrue(backend.contains(TestProjectMemberPerm.API_VIEW.getCode()));
        assertTrue(backend.contains(TestProjectMemberPerm.API_EDIT.getCode()));
        assertTrue(backend.contains(TestProjectMemberPerm.AI_FLOW_WRITE.getCode()));
        assertTrue(backend.contains(TestProjectMemberPerm.PLUGIN_API_IMPORT.getCode()));
        assertTrue(backend.contains(TestProjectMemberPerm.MCP_FLOW_WRITE.getCode()));
    }

    /**
     * 前提：frontend 期望为 9 个权限。
     * 期望：含 TOKEN_USE 与 MCP_READ，不含 FLOW_EDIT 与 MCP_RUN。
     */
    @Test
    @Order(4)
    @DisplayName("frontend 种子为查看+执行+token+mcp:read")
    void frontend_viewRunTokenMcpRead() {
        Set<String> frontend = frontendSeed();
        assertEquals(9, frontend.size());
        assertTrue(frontend.contains(TestProjectMemberPerm.TOKEN_USE.getCode()));
        assertTrue(frontend.contains(TestProjectMemberPerm.MCP_READ.getCode()));
        assertFalse(frontend.contains(TestProjectMemberPerm.FLOW_EDIT.getCode()));
        assertFalse(frontend.contains(TestProjectMemberPerm.MCP_RUN.getCode()));
    }

    /**
     * 前提：tester 期望为 7 个权限。
     * 期望：可跑流/套件，不含 TOKEN_USE 与 MCP_READ。
     */
    @Test
    @Order(5)
    @DisplayName("tester 种子为查看+执行，无 token/MCP")
    void tester_viewRunWithoutTokenOrMcp() {
        Set<String> tester = testerSeed();
        assertEquals(7, tester.size());
        assertFalse(tester.contains(TestProjectMemberPerm.TOKEN_USE.getCode()));
        assertFalse(tester.contains(TestProjectMemberPerm.MCP_READ.getCode()));
        assertTrue(tester.contains(TestProjectMemberPerm.FLOW_RUN.getCode()));
        assertTrue(tester.contains(TestProjectMemberPerm.SUITE_RUN.getCode()));
    }

    /**
     * 前提：调用 treeCatalog。
     * 期望：含「项目」「Token / 插件 / MCP」父节点，且 AI 分组下有子权限。
     */
    @Test
    @Order(6)
    @DisplayName("权限目录树含项目分组与 Token/插件/MCP 分组")
    void catalog_tree() {
        List<Map<String, Object>> tree = TestProjectMemberPerm.treeCatalog();
        assertFalse(tree.isEmpty());
        Set<String> labels = tree.stream()
                .map(n -> String.valueOf(n.get("label")))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        assertTrue(labels.contains("项目"));
        assertTrue(labels.contains("Token / 插件 / MCP"));
        Map<String, Object> ai = tree.stream()
                .filter(n -> "AI".equals(n.get("label")))
                .findFirst()
                .orElseThrow();
        assertFalse(((List<?>) ai.get("children")).isEmpty());
    }
}
