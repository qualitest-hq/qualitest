package com.qualitest.project.service.impl;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.service.ITestProjectMemberRoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 测谁：TestProjectMemberServiceImpl.requireCapability 门禁。
 * 边界：有权限放行；缺权限抛 ServiceException；sysAdmin 虚拟角色全开。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectMemberRequireCapabilityTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectMemberRequireCapabilityTest {

    @Mock
    private ITestProjectMemberRoleService testProjectMemberRoleService;

    @InjectMocks
    private TestProjectMemberServiceImpl memberService;

    /**
     * 前提：角色权限集含 flow:run。
     * 期望：requireCapability 不抛异常。
     */
    @Test
    @Order(1)
    @DisplayName("角色含目标权限时 requireCapability 放行")
    void requireCapability_allowsWhenPermPresent() {
        TestProjectMemberServiceImpl spy = spy(memberService);
        doReturn(TestProjectMemberRole.BACKEND.getCode())
                .when(spy).getCheckProjectMemberRole(anyLong(), anyLong());
        when(testProjectMemberRoleService.listPermCodesByRoleKey(TestProjectMemberRole.BACKEND.getCode()))
                .thenReturn(Set.of(TestProjectMemberPerm.FLOW_RUN.getCode()));

        assertDoesNotThrow(() -> spy.requireCapability(1L, 9L, TestProjectMemberPerm.FLOW_RUN));
    }

    /**
     * 前提：角色仅有 api:view，请求 flow:edit。
     * 期望：抛出 ServiceException。
     */
    @Test
    @Order(2)
    @DisplayName("角色缺权限时 requireCapability 抛错")
    void requireCapability_rejectsWhenPermMissing() {
        TestProjectMemberServiceImpl spy = spy(memberService);
        doReturn(TestProjectMemberRole.TESTER.getCode())
                .when(spy).getCheckProjectMemberRole(anyLong(), anyLong());
        when(testProjectMemberRoleService.listPermCodesByRoleKey(TestProjectMemberRole.TESTER.getCode()))
                .thenReturn(new LinkedHashSet<>(Set.of(TestProjectMemberPerm.API_VIEW.getCode())));

        assertThrows(ServiceException.class,
                () -> spy.requireCapability(1L, 9L, TestProjectMemberPerm.FLOW_EDIT));
    }

    /**
     * 前提：虚拟 sysAdmin 角色解析为全量权限 code。
     * 期望：任意权限校验均放行。
     */
    @Test
    @Order(3)
    @DisplayName("sysAdmin 角色权限目录为全量时放行任意点")
    void requireCapability_sysAdminViaRoleServiceAllCodes() {
        TestProjectMemberServiceImpl spy = spy(memberService);
        doReturn(TestProjectMemberRole.SYS_ADMIN.getCode())
                .when(spy).getCheckProjectMemberRole(anyLong(), anyLong());
        when(testProjectMemberRoleService.listPermCodesByRoleKey(TestProjectMemberRole.SYS_ADMIN.getCode()))
                .thenReturn(new LinkedHashSet<>(TestProjectMemberPerm.allCodes()));

        assertDoesNotThrow(() -> spy.requireCapability(1L, 1L, TestProjectMemberPerm.PROJECT_DELETE));
    }
}
