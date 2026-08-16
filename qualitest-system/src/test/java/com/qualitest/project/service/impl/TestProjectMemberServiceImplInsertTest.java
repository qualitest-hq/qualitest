package com.qualitest.project.service.impl;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectMember;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.mapper.TestProjectMemberMapper;
import com.qualitest.project.params.TestProjectMemberParams;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectUserSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 TestProjectMemberServiceImpl：成员判重、所有者转让与「恰好一名所有者」不变量。
 * 边界：重复用户、未选用户、按 userId 降级、禁止拆掉最后一名 owner、非 owner 改角色不触发降级。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectMemberServiceImplInsertTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectMemberServiceImplInsertTest {

    private static final Long PROJECT_ID = 2088803096141680640L;
    private static final Long OWNER_USER_ID = 1L;
    private static final Long NEW_USER_ID = 2L;
    private static final Long OWNER_MEMBER_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 200L;

    @Mock
    private TestProjectMemberMapper testProjectMemberMapper;
    @Mock
    private TestProjectMapper testProjectMapper;
    @Mock
    private ITestProjectUserSettingService testProjectUserSettingService;
    @Mock
    private ITestProjectEnvService testProjectEnvService;

    private TestProjectMemberServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestProjectMemberServiceImpl();
        ReflectionTestUtils.setField(service, "testProjectMemberMapper", testProjectMemberMapper);
        ReflectionTestUtils.setField(service, "testProjectMapper", testProjectMapper);
        ReflectionTestUtils.setField(service, "testProjectUserSettingService", testProjectUserSettingService);
        ReflectionTestUtils.setField(service, "testProjectEnvService", testProjectEnvService);
    }

    /**
     * 前提：项目里已有成员，新增另一用户为测试员。
     * 期望：按项目+用户查重通过；不触发所有者转让。
     */
    @Test
    @Order(1)
    @DisplayName("项目已有其他成员时仍可新增新用户")
    void insert_allowsSecondMember() {
        when(testProjectMemberMapper.selectTestProjectMemberOne(any(TestProjectMemberParams.class))).thenReturn(null);
        when(testProjectMemberMapper.insertTestProjectMember(any(TestProjectMember.class))).thenReturn(1);
        when(testProjectEnvService.insertTestProjectEnv(any())).thenReturn(1);
        when(testProjectUserSettingService.insertTestProjectUserSetting(any())).thenReturn(1);

        int flag = service.insertTestProjectMember(TestProjectMember.builder()
                .testProjectId(PROJECT_ID)
                .userId(NEW_USER_ID)
                .memberRole(TestProjectMemberRole.TESTER.getCode())
                .build());

        assertEquals(1, flag);
        ArgumentCaptor<TestProjectMemberParams> captor = ArgumentCaptor.forClass(TestProjectMemberParams.class);
        verify(testProjectMemberMapper).selectTestProjectMemberOne(captor.capture());
        TestProjectMemberParams lookup = captor.getValue();
        assertEquals(PROJECT_ID, lookup.getTestProjectId());
        assertEquals(NEW_USER_ID, lookup.getUserId());
        assertNull(lookup.getTestProjectMemberId());
        verify(testProjectMemberMapper, never()).demoteOtherOwnersToAdmin(any(), any());
        verify(testProjectMapper, never()).updateOwnerId(any(), any());
    }

    /**
     * 前提：同一用户已是该项目成员。
     * 期望：拒绝新增。
     */
    @Test
    @Order(2)
    @DisplayName("同一用户重复加入时拒绝")
    void insert_rejectsDuplicateUser() {
        when(testProjectMemberMapper.selectTestProjectMemberOne(any(TestProjectMemberParams.class)))
                .thenReturn(TestProjectMember.builder()
                        .testProjectId(PROJECT_ID)
                        .userId(OWNER_USER_ID)
                        .memberRole(TestProjectMemberRole.OWNER.getCode())
                        .build());

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.insertTestProjectMember(TestProjectMember.builder()
                        .testProjectId(PROJECT_ID)
                        .userId(OWNER_USER_ID)
                        .memberRole(TestProjectMemberRole.TESTER.getCode())
                        .build()));

        assertEquals("该用户已是本项目成员", ex.getMessage());
        verify(testProjectMemberMapper, never()).insertTestProjectMember(any());
    }

    /**
     * 前提：未选择用户。
     * 期望：拒绝新增。
     */
    @Test
    @Order(3)
    @DisplayName("未选择用户时拒绝")
    void insert_requiresUserId() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.insertTestProjectMember(TestProjectMember.builder()
                        .testProjectId(PROJECT_ID)
                        .memberRole(TestProjectMemberRole.TESTER.getCode())
                        .build()));

        assertEquals("请选择用户", ex.getMessage());
        verify(testProjectMemberMapper, never()).selectTestProjectMemberOne(any());
    }

    /**
     * 前提：项目已有所有者，再新增一名角色为 owner 的成员。
     * 期望：按 userId 降级原 owner，回写 owner_id，断言恰好一名。
     */
    @Test
    @Order(4)
    @DisplayName("新增所有者时按 userId 降级原 owner")
    void insert_demotesPreviousOwnerByUserId() {
        when(testProjectMemberMapper.selectTestProjectMemberOne(any(TestProjectMemberParams.class))).thenReturn(null);
        when(testProjectMemberMapper.demoteOtherOwnersToAdmin(PROJECT_ID, NEW_USER_ID)).thenReturn(1);
        when(testProjectMapper.updateOwnerId(PROJECT_ID, NEW_USER_ID)).thenReturn(1);
        when(testProjectMemberMapper.insertTestProjectMember(any(TestProjectMember.class))).thenReturn(1);
        when(testProjectEnvService.insertTestProjectEnv(any())).thenReturn(1);
        when(testProjectUserSettingService.generateProjectToken(PROJECT_ID, NEW_USER_ID)).thenReturn("tok");
        when(testProjectUserSettingService.insertTestProjectUserSetting(any())).thenReturn(1);
        when(testProjectMemberMapper.countOwners(PROJECT_ID)).thenReturn(1);

        int flag = service.insertTestProjectMember(TestProjectMember.builder()
                .testProjectId(PROJECT_ID)
                .userId(NEW_USER_ID)
                .memberRole(TestProjectMemberRole.OWNER.getCode())
                .build());

        assertEquals(1, flag);
        verify(testProjectMemberMapper).demoteOtherOwnersToAdmin(PROJECT_ID, NEW_USER_ID);
        verify(testProjectMapper).updateOwnerId(PROJECT_ID, NEW_USER_ID);
        verify(testProjectMemberMapper).countOwners(PROJECT_ID);
    }

    /**
     * 前提：把另一名成员改成 owner。
     * 期望：按 userId 降级原 owner，回写 owner_id。
     */
    @Test
    @Order(5)
    @DisplayName("修改为所有者时按 userId 降级原 owner")
    void update_demotesPreviousOwnerByUserId() {
        when(testProjectMemberMapper.selectTestProjectMemberById(OTHER_MEMBER_ID))
                .thenReturn(TestProjectMember.builder()
                        .testProjectMemberId(OTHER_MEMBER_ID)
                        .testProjectId(PROJECT_ID)
                        .userId(NEW_USER_ID)
                        .memberRole(TestProjectMemberRole.ADMIN.getCode())
                        .build());
        when(testProjectMemberMapper.demoteOtherOwnersToAdmin(PROJECT_ID, NEW_USER_ID)).thenReturn(1);
        when(testProjectMapper.updateOwnerId(PROJECT_ID, NEW_USER_ID)).thenReturn(1);
        when(testProjectMemberMapper.updateTestProjectMember(any(TestProjectMember.class))).thenReturn(1);
        when(testProjectMemberMapper.countOwners(PROJECT_ID)).thenReturn(1);

        int flag = service.updateTestProjectMember(TestProjectMember.builder()
                .testProjectMemberId(OTHER_MEMBER_ID)
                .memberRole(TestProjectMemberRole.OWNER.getCode())
                .build());

        assertEquals(1, flag);
        verify(testProjectMemberMapper).demoteOtherOwnersToAdmin(eq(PROJECT_ID), eq(NEW_USER_ID));
        verify(testProjectMapper).updateOwnerId(PROJECT_ID, NEW_USER_ID);
    }

    /**
     * 前提：把最后一名 owner 改成 tester。
     * 期望：拒绝，提示先转让。
     */
    @Test
    @Order(6)
    @DisplayName("不能把最后一名所有者降级")
    void update_rejectsDemoteLastOwner() {
        when(testProjectMemberMapper.selectTestProjectMemberById(OWNER_MEMBER_ID))
                .thenReturn(TestProjectMember.builder()
                        .testProjectMemberId(OWNER_MEMBER_ID)
                        .testProjectId(PROJECT_ID)
                        .userId(OWNER_USER_ID)
                        .memberRole(TestProjectMemberRole.OWNER.getCode())
                        .build());

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.updateTestProjectMember(TestProjectMember.builder()
                        .testProjectMemberId(OWNER_MEMBER_ID)
                        .memberRole(TestProjectMemberRole.TESTER.getCode())
                        .build()));

        assertEquals("项目必须保留一名所有者，请先将所有权转让给其他成员", ex.getMessage());
        verify(testProjectMemberMapper, never()).updateTestProjectMember(any());
        verify(testProjectMemberMapper, never()).demoteOtherOwnersToAdmin(any(), any());
    }

    /**
     * 前提：删除所有者成员。
     * 期望：拒绝。
     */
    @Test
    @Order(7)
    @DisplayName("不能删除所有者")
    void delete_rejectsOwner() {
        when(testProjectMemberMapper.selectTestProjectMemberList(any(TestProjectMember.class)))
                .thenReturn(List.of(TestProjectMember.builder()
                        .testProjectMemberId(OWNER_MEMBER_ID)
                        .testProjectId(PROJECT_ID)
                        .userId(OWNER_USER_ID)
                        .memberRole(TestProjectMemberRole.OWNER.getCode())
                        .build()));

        ServiceException ex = assertThrows(ServiceException.class, () ->
                service.logicDeleteTestProjectMemberByIdList(List.of(OWNER_MEMBER_ID)));

        assertEquals("项目必须保留一名所有者，请先将所有权转让给其他成员", ex.getMessage());
        verify(testProjectMemberMapper, never()).logicDeleteTestProjectMemberByIdList(any());
    }

    /**
     * 前提：把管理员改成开发者。
     * 期望：不触发所有者转让。
     */
    @Test
    @Order(8)
    @DisplayName("非所有者改角色不触发降级")
    void update_nonOwnerRoleChangeSkipsTransfer() {
        when(testProjectMemberMapper.selectTestProjectMemberById(OTHER_MEMBER_ID))
                .thenReturn(TestProjectMember.builder()
                        .testProjectMemberId(OTHER_MEMBER_ID)
                        .testProjectId(PROJECT_ID)
                        .userId(NEW_USER_ID)
                        .memberRole(TestProjectMemberRole.ADMIN.getCode())
                        .build());
        when(testProjectMemberMapper.updateTestProjectMember(any(TestProjectMember.class))).thenReturn(1);

        int flag = service.updateTestProjectMember(TestProjectMember.builder()
                .testProjectMemberId(OTHER_MEMBER_ID)
                .memberRole(TestProjectMemberRole.DEVELOPER.getCode())
                .build());

        assertEquals(1, flag);
        verify(testProjectMemberMapper, never()).demoteOtherOwnersToAdmin(any(), any());
        verify(testProjectMapper, never()).updateOwnerId(any(), any());
        verify(testProjectMemberMapper, never()).countOwners(any());
    }
}
