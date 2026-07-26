package com.qualitest.ai.service.impl;

import com.qualitest.ai.domain.AiLlmVendor;
import com.qualitest.ai.llm.discovery.ModelDiscoveryService;
import com.qualitest.ai.mapper.AiLlmVendorMapper;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static com.qualitest.flow.support.FlowTestSections.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiLlmVendorServiceImpl} 单元测试。
 * <p>
 * 覆盖厂商删除保护与发现缓存失效：
 * <ul>
 *   <li>物理删除：{@code builtin_status=1} 的内置厂商不可删除；自定义厂商可删除并清除发现缓存</li>
 *   <li>批量逻辑删除：列表中含内置厂商时整批拒绝</li>
 * </ul>
 * Mapper 与 {@link ModelDiscoveryService} 使用 Mock，不访问数据库与 Redis。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=AiLlmVendorServiceImplTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiLlmVendorServiceImplTest {

    private AiLlmVendorMapper mapper;
    private ModelDiscoveryService modelDiscoveryService;
    private AiLlmVendorServiceImpl service;

    /**
     * 构造被测 Service 并注入 Mock 的 Mapper 与 ModelDiscoveryService。
     */
    @BeforeEach
    void setUp() {
        mapper = mock(AiLlmVendorMapper.class);
        modelDiscoveryService = mock(ModelDiscoveryService.class);
        service = new AiLlmVendorServiceImpl();
        ReflectionTestUtils.setField(service, "aiLlmVendorMapper", mapper);
        ReflectionTestUtils.setField(service, "modelDiscoveryService", modelDiscoveryService);
    }

    /**
     * 目标厂商 {@code builtin_status=1}（系统预置）。
     * 期望：{@link AiLlmVendorServiceImpl#deleteAiLlmVendorById(Long)} 抛出 {@link ServiceException}，
     * 消息含「内置」；不调用 Mapper 物理删除，也不失效发现缓存。
     */
    @Test
    @Order(1)
    void delete_builtinVendor_throws() {
        begin("delete_builtinVendor_throws");
        when(mapper.selectAiLlmVendorById(8001L)).thenReturn(AiLlmVendor.builder()
                .aiLlmVendorId(8001L)
                .builtinStatus(1)
                .build());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteAiLlmVendorById(8001L));
        assertTrue(ex.getMessage().contains("内置"));
        log("error=" + quote(ex.getMessage()));
        end("delete_builtinVendor_throws");
    }

    /**
     * 目标厂商 {@code builtin_status=0}（用户自建）。
     * 期望：{@link AiLlmVendorServiceImpl#deleteAiLlmVendorById(Long)} 通过删除校验；
     * 调用 Mapper 物理删除并返回 1；调用 {@link ModelDiscoveryService#invalidateDiscoverCache(Long)} 清除该厂商的发现缓存。
     */
    @Test
    @Order(2)
    void delete_customVendor_invalidatesCache() {
        begin("delete_customVendor_invalidatesCache");
        when(mapper.selectAiLlmVendorById(8002L)).thenReturn(AiLlmVendor.builder()
                .aiLlmVendorId(8002L)
                .builtinStatus(0)
                .build());
        when(mapper.deleteAiLlmVendorById(8002L)).thenReturn(1);

        assertEquals(1, service.deleteAiLlmVendorById(8002L));
        verify(modelDiscoveryService).invalidateDiscoverCache(8002L);
        log("deletedVendorId=8002 cacheInvalidated=true");
        end("delete_customVendor_invalidatesCache");
    }

    /**
     * 批量逻辑删除列表中包含 {@code builtin_status=1} 的内置厂商。
     * 期望：{@link AiLlmVendorServiceImpl#logicDeleteAiLlmVendorByIdList(List)} 抛出 {@link ServiceException}；
     * 不执行任何逻辑删除。
     */
    @Test
    @Order(3)
    void logicDelete_builtinVendorList_throws() {
        begin("logicDelete_builtinVendorList_throws");
        when(mapper.selectAiLlmVendorById(8003L)).thenReturn(AiLlmVendor.builder()
                .aiLlmVendorId(8003L)
                .builtinStatus(1)
                .build());

        assertThrows(ServiceException.class, () -> service.logicDeleteAiLlmVendorByIdList(List.of(8003L)));
        log("blockedBuiltinVendorId=8003");
        end("logicDelete_builtinVendorList_throws");
    }
}
