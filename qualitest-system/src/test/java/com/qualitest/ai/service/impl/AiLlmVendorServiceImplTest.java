package com.qualitest.ai.service.impl;

import com.qualitest.ai.domain.AiLlmVendor;
import com.qualitest.ai.llm.discovery.ModelDiscoveryService;
import com.qualitest.ai.mapper.AiLlmVendorMapper;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 AiLlmVendorServiceImpl：内置厂商删除保护与发现缓存失效。
 * 边界：Mapper/Discovery Mock；无真实 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AiLlmVendorServiceImplTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiLlmVendorServiceImplTest {

    private AiLlmVendorMapper mapper;
    private ModelDiscoveryService modelDiscoveryService;
    private AiLlmVendorServiceImpl service;

    /**
     * 每个用例重建 Service 并注入 Mock。
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
     * 前提：厂商 builtin_status=1。
     * 期望：deleteById 抛 ServiceException（消息含「内置」）。
     */
    @Test
    @Order(1)
    @DisplayName("内置厂商禁止删除")
    void delete_builtinVendor_throws() {
        when(mapper.selectAiLlmVendorById(8001L)).thenReturn(AiLlmVendor.builder()
                .aiLlmVendorId(8001L)
                .builtinStatus(1)
                .build());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteAiLlmVendorById(8001L));
        assertTrue(ex.getMessage().contains("内置"));
    }

    /**
     * 前提：厂商 builtin_status=0；Mapper 删除返回 1。
     * 期望：返回 1；调用 invalidateDiscoverCache(8002)。
     */
    @Test
    @Order(2)
    @DisplayName("自定义厂商删除并失效缓存")
    void delete_customVendor_invalidatesCache() {
        when(mapper.selectAiLlmVendorById(8002L)).thenReturn(AiLlmVendor.builder()
                .aiLlmVendorId(8002L)
                .builtinStatus(0)
                .build());
        when(mapper.deleteAiLlmVendorById(8002L)).thenReturn(1);

        assertEquals(1, service.deleteAiLlmVendorById(8002L));
        verify(modelDiscoveryService).invalidateDiscoverCache(8002L);
    }

    /**
     * 前提：批量逻辑删除列表含 builtin_status=1 的厂商。
     * 期望：抛 ServiceException，不执行逻辑删除。
     */
    @Test
    @Order(3)
    @DisplayName("批量逻辑删除含内置时抛异常")
    void logicDelete_builtinVendorList_throws() {
        when(mapper.selectAiLlmVendorById(8003L)).thenReturn(AiLlmVendor.builder()
                .aiLlmVendorId(8003L)
                .builtinStatus(1)
                .build());

        assertThrows(ServiceException.class, () -> service.logicDeleteAiLlmVendorByIdList(List.of(8003L)));
    }
}
