package com.qualitest.ai.service.impl;

import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.domain.AiLlmModel;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.mapper.AiLlmModelMapper;
import com.qualitest.ai.result.AiLlmModelResolveResult;
import com.qualitest.ai.result.AiModelsListResult;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 AiLlmModelServiceImpl：resolve 组装配置、按厂商分组列表、内置模型删除保护。
 * 边界：Mapper/Config Mock；无真实 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AiLlmModelServiceImplTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiLlmModelServiceImplTest {

    private AiLlmModelMapper mapper;
    private AiLlmConfigService configService;
    private AiLlmModelServiceImpl service;

    /**
     * 每个用例重建 Service；Config 固定超时与 maxTokens。
     */
    @BeforeEach
    void setUp() {
        mapper = mock(AiLlmModelMapper.class);
        configService = mock(AiLlmConfigService.class);
        service = new AiLlmModelServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "aiLlmModelMapper", mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "aiLlmConfigService", configService);
        when(configService.getMaxTokens()).thenReturn(8192);
        when(configService.getConnectTimeoutMs()).thenReturn(10000);
        when(configService.getReadTimeoutMs()).thenReturn(120000);
        when(configService.getWriteTimeoutMs()).thenReturn(30000);
    }

    /**
     * 前提：模型/厂商均启用未删除，且有 baseUrl、apiKey。
     * 期望：resolve 返回含 modelId、modelName、baseUrl、maxTokens 的配置。
     */
    @Test
    @Order(1)
    @DisplayName("启用模型 resolve 返回完整配置")
    void resolve_enabledModel_returnsConfig() {
        AiLlmModelResolveResult row = AiLlmModelResolveResult.builder()
                .aiLlmModelId(1001L)
                .aiLlmVendorId(2001L)
                .modelName("gpt-4o-mini")
                .vendorName("OpenAI")
                .provider("openai_compatible")
                .baseUrl("https://api.openai.com/v1")
                .apiKey("sk-test")
                .modelEnableStatus(1)
                .vendorEnableStatus(1)
                .modelDelStatus(0)
                .vendorDelStatus(0)
                .build();
        when(mapper.selectAiLlmModelResolve(1001L)).thenReturn(row);

        LlmModelConfig cfg = service.resolve(1001L);

        assertEquals(1001L, cfg.getAiLlmModelId());
        assertEquals("gpt-4o-mini", cfg.getModelName());
        assertEquals("https://api.openai.com/v1", cfg.getBaseUrl());
        assertEquals(8192, cfg.getMaxTokens());
    }

    /**
     * 前提：modelEnableStatus=0。
     * 期望：抛 LlmClientException，消息含「未启用」。
     */
    @Test
    @Order(2)
    @DisplayName("未启用模型抛 LlmClientException")
    void resolve_disabledModel_throws() {
        when(mapper.selectAiLlmModelResolve(1002L)).thenReturn(AiLlmModelResolveResult.builder()
                .modelEnableStatus(0)
                .vendorEnableStatus(1)
                .modelDelStatus(0)
                .vendorDelStatus(0)
                .build());

        LlmClientException ex = assertThrows(LlmClientException.class, () -> service.resolve(1002L));
        assertTrue(ex.getMessage().contains("未启用"));
    }

    /**
     * 前提：两条启用模型分属 sort_num=10/20 的两厂商。
     * 期望：2 个分组；首组 OpenAI；defaultModelId=1001。
     */
    @Test
    @Order(3)
    @DisplayName("按厂商分组列出模型")
    void listModelsGrouped_groupsByVendor() {
        when(mapper.selectEnabledAiLlmModelsWithVendor()).thenReturn(List.of(
                AiLlmModelResolveResult.builder()
                        .aiLlmModelId(1001L)
                        .aiLlmVendorId(2001L)
                        .vendorName("OpenAI")
                        .modelName("gpt-4o-mini")
                        .vendorSortNum(10)
                        .modelSortNum(10)
                        .build(),
                AiLlmModelResolveResult.builder()
                        .aiLlmModelId(1003L)
                        .aiLlmVendorId(2002L)
                        .vendorName("DeepSeek")
                        .modelName("deepseek-chat")
                        .vendorSortNum(20)
                        .modelSortNum(10)
                        .build()
        ));

        AiModelsListResult result = service.listModelsGrouped();

        assertEquals(2, result.getVendors().size());
        assertEquals("OpenAI", result.getVendors().get(0).getVendorName());
        assertEquals(1001L, result.getDefaultModelId());
        assertEquals(1, result.getVendors().get(0).getModels().size());
    }

    /**
     * 前提：模型 builtin_status=1。
     * 期望：deleteById 抛 ServiceException（消息含「内置」）。
     */
    @Test
    @Order(4)
    @DisplayName("内置模型禁止删除")
    void delete_builtinModel_throws() {
        when(mapper.selectAiLlmModelById(9001L)).thenReturn(AiLlmModel.builder()
                .aiLlmModelId(9001L)
                .builtinStatus(1)
                .build());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteAiLlmModelById(9001L));
        assertTrue(ex.getMessage().contains("内置"));
    }

    /**
     * 前提：模型 builtin_status=0；Mapper 删除返回 1。
     * 期望：返回 1。
     */
    @Test
    @Order(5)
    @DisplayName("自定义模型允许删除")
    void delete_customModel_allowed() {
        when(mapper.selectAiLlmModelById(9002L)).thenReturn(AiLlmModel.builder()
                .aiLlmModelId(9002L)
                .builtinStatus(0)
                .build());
        when(mapper.deleteAiLlmModelById(9002L)).thenReturn(1);

        assertEquals(1, service.deleteAiLlmModelById(9002L));
    }
}
