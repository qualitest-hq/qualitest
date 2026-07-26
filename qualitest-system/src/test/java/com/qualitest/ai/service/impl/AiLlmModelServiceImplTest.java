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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static com.qualitest.flow.support.FlowTestSections.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AiLlmModelServiceImpl} 单元测试。
 * <p>
 * 覆盖三类能力：
 * <ul>
 *   <li>{@link AiLlmModelServiceImpl#resolve(Long)}：按模型主键组装运行时 {@link LlmModelConfig}，并校验启用/删除/密钥等前置条件</li>
 *   <li>{@link AiLlmModelServiceImpl#listModelsGrouped()}：返回按厂商分组的启用模型列表及默认模型 ID</li>
 *   <li>物理删除保护：{@code builtin_status=1} 的内置模型不可通过 {@code delete*} 接口删除</li>
 * </ul>
 * Mapper 与 ConfigService 使用 Mock，不访问数据库。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=AiLlmModelServiceImplTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiLlmModelServiceImplTest {

    private AiLlmModelMapper mapper;
    private AiLlmConfigService configService;
    private AiLlmModelServiceImpl service;

    /**
     * 构造被测 Service 并注入 Mock 依赖；ConfigService 返回固定的超时与 maxTokens 配置。
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
     * 模型与所属厂商均启用、均未逻辑删除，且厂商已配置 baseUrl 与 apiKey。
     * 期望：{@link AiLlmModelServiceImpl#resolve(Long)} 返回完整 {@link LlmModelConfig}，
     * 含 modelId、modelName、baseUrl 及 ConfigService 提供的 maxTokens。
     */
    @Test
    @Order(1)
    void resolve_enabledModel_returnsConfig() {
        begin("resolve_enabledModel_returnsConfig");
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
        log("modelId=" + cfg.getAiLlmModelId()
                + " modelName=" + quote(cfg.getModelName())
                + " baseUrl=" + quote(cfg.getBaseUrl())
                + " maxTokens=" + cfg.getMaxTokens());
        end("resolve_enabledModel_returnsConfig");
    }

    /**
     * 模型 {@code enable_status=0}（未启用），厂商仍为启用状态。
     * 期望：{@link AiLlmModelServiceImpl#resolve(Long)} 抛出 {@link LlmClientException}，消息含「未启用」。
     */
    @Test
    @Order(2)
    void resolve_disabledModel_throws() {
        begin("resolve_disabledModel_throws");
        when(mapper.selectAiLlmModelResolve(1002L)).thenReturn(AiLlmModelResolveResult.builder()
                .modelEnableStatus(0)
                .vendorEnableStatus(1)
                .modelDelStatus(0)
                .vendorDelStatus(0)
                .build());

        LlmClientException ex = assertThrows(LlmClientException.class, () -> service.resolve(1002L));
        assertTrue(ex.getMessage().contains("未启用"));
        log("error=" + quote(ex.getMessage()));
        end("resolve_disabledModel_throws");
    }

    /**
     * 两条启用模型分属两个厂商，厂商 sort_num 分别为 10 与 20。
     * 期望：{@link AiLlmModelServiceImpl#listModelsGrouped()} 返回 2 个厂商分组；
     * 第一组为 sort_num 较小的 OpenAI；{@code defaultModelId} 取全局排序后首个模型（1001）。
     */
    @Test
    @Order(3)
    void listModelsGrouped_groupsByVendor() {
        begin("listModelsGrouped_groupsByVendor");
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
        log("vendors=" + result.getVendors().size()
                + " firstVendor=" + quote(result.getVendors().get(0).getVendorName())
                + " defaultModelId=" + result.getDefaultModelId());
        end("listModelsGrouped_groupsByVendor");
    }

    /**
     * 目标模型 {@code builtin_status=1}（系统内置）。
     * 期望：{@link AiLlmModelServiceImpl#deleteAiLlmModelById(Long)} 抛出 {@link ServiceException}，
     * 消息含「内置」；不调用 Mapper 物理删除。
     */
    @Test
    @Order(4)
    void delete_builtinModel_throws() {
        begin("delete_builtinModel_throws");
        when(mapper.selectAiLlmModelById(9001L)).thenReturn(AiLlmModel.builder()
                .aiLlmModelId(9001L)
                .builtinStatus(1)
                .build());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteAiLlmModelById(9001L));
        assertTrue(ex.getMessage().contains("内置"));
        log("error=" + quote(ex.getMessage()));
        end("delete_builtinModel_throws");
    }

    /**
     * 目标模型 {@code builtin_status=0}（用户自建或发现同步入库）。
     * 期望：{@link AiLlmModelServiceImpl#deleteAiLlmModelById(Long)} 通过删除校验并调用 Mapper 物理删除，返回 1。
     */
    @Test
    @Order(5)
    void delete_customModel_allowed() {
        begin("delete_customModel_allowed");
        when(mapper.selectAiLlmModelById(9002L)).thenReturn(AiLlmModel.builder()
                .aiLlmModelId(9002L)
                .builtinStatus(0)
                .build());
        when(mapper.deleteAiLlmModelById(9002L)).thenReturn(1);

        assertEquals(1, service.deleteAiLlmModelById(9002L));
        log("deletedModelId=9002");
        end("delete_customModel_allowed");
    }
}
