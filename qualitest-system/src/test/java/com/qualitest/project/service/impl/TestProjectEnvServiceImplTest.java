package com.qualitest.project.service.impl;

import com.qualitest.api.util.HttpEgressAllowlist;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.sync.FlowExternalChangePublisher;
import com.qualitest.project.constant.TestProjectConstants;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectEnvMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测环境保存时的出站 URL 校验。
 * 边界：Mockito，无库；占位 URL 跳过，非法目标在白名单开启时拒绝。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectEnvServiceImplTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectEnvServiceImplTest {

    @Mock
    private TestProjectEnvMapper testProjectEnvMapper;

    @Mock
    private FlowExternalChangePublisher flowExternalChangePublisher;

    @Mock
    private HttpEgressAllowlist httpEgressAllowlist;

    @InjectMocks
    private TestProjectEnvServiceImpl service;

    /**
     * 前提：envUrl 为建项占位 http://127.0.0.1。
     * 期望：不调用出站校验，插入成功。
     */
    @Test
    @Order(1)
    @DisplayName("新增：占位 URL 跳过出站校验")
    void insert_skipsPlaceholderEnvUrl() {
        when(testProjectEnvMapper.insertTestProjectEnv(any())).thenReturn(1);

        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectId(1L)
                .envName("默认")
                .envUrl(TestProjectConstants.DEFAULT_ENV_URL_PLACEHOLDER)
                .build();

        assertDoesNotThrow(() -> service.insertTestProjectEnv(env));
        verify(httpEgressAllowlist, never()).check(anyString());
    }

    /**
     * 前提：白名单校验返回拒绝原因。
     * 期望：修改抛 ServiceException，不写库。
     */
    @Test
    @Order(2)
    @DisplayName("修改：出站校验失败则拒绝保存")
    void update_rejectsDisallowedEnvUrl() {
        when(httpEgressAllowlist.check("http://evil.com")).thenReturn("目标不在 HTTP 出口白名单内: evil.com:80");

        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectEnvId(2L)
                .testProjectId(1L)
                .envName("坏环境")
                .envUrl("http://evil.com")
                .build();

        assertThrows(ServiceException.class, () -> service.updateTestProjectEnv(env));
        verify(testProjectEnvMapper, never()).updateTestProjectEnv(any());
    }

    /**
     * 前提：出站校验通过。
     * 期望：修改写库成功。
     */
    @Test
    @Order(3)
    @DisplayName("修改：出站校验通过则保存")
    void update_allowsListedEnvUrl() {
        when(httpEgressAllowlist.check("http://host.docker.internal:8801")).thenReturn(null);
        when(testProjectEnvMapper.updateTestProjectEnv(any())).thenReturn(1);

        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectEnvId(3L)
                .testProjectId(1L)
                .envName("演示")
                .envUrl("http://host.docker.internal:8801")
                .build();

        assertDoesNotThrow(() -> service.updateTestProjectEnv(env));
        verify(testProjectEnvMapper).updateTestProjectEnv(any());
    }

    /**
     * 前提：本次未传 envUrl（null）。
     * 期望：跳过出站校验（避免局部更新误伤）。
     */
    @Test
    @Order(4)
    @DisplayName("修改：envUrl 为 null 时不校验")
    void update_skipsWhenEnvUrlNull() {
        when(testProjectEnvMapper.updateTestProjectEnv(any())).thenReturn(1);
        when(testProjectEnvMapper.selectTestProjectEnvById(4L)).thenReturn(
                TestProjectEnv.builder().testProjectEnvId(4L).testProjectId(1L).build());

        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectEnvId(4L)
                .envName("只改名")
                .build();

        assertDoesNotThrow(() -> service.updateTestProjectEnv(env));
        verify(httpEgressAllowlist, never()).check(anyString());
    }
}
