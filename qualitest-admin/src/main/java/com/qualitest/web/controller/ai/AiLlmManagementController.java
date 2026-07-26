package com.qualitest.web.controller.ai;

import com.qualitest.ai.llm.discovery.ModelDiscoveryService;
import com.qualitest.ai.params.AiLlmSyncModelsParams;
import com.qualitest.ai.params.AiLlmTestConnectionParams;
import com.qualitest.ai.result.AiLlmDiscoverModelsResult;
import com.qualitest.ai.result.AiLlmTestConnectionResult;
import com.qualitest.ai.result.ProviderTemplateResult;
import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.enums.BusinessType;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 厂商模板、连通检测与模型发现接口。
 */
@RestController
@RequestMapping("/ai/llm")
@AllArgsConstructor
public class AiLlmManagementController extends BaseController {

    private final ModelDiscoveryService modelDiscoveryService;

    /**
     * 读取厂商模板列表（供新增表单默认值）。
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmVendor:list')")
    @GetMapping("/provider-templates")
    public R<List<ProviderTemplateResult>> listProviderTemplates() {
        return ok(modelDiscoveryService.listProviderTemplates());
    }

    /**
     * 检测厂商连通性（不持久化）。
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmVendor:add') or @ss.hasPermi('ai:aiLlmVendor:edit')")
    @PostMapping("/vendor/test-connection")
    public R<AiLlmTestConnectionResult> testConnection(@RequestBody AiLlmTestConnectionParams params) {
        return ok(modelDiscoveryService.testConnection(params));
    }

    /**
     * 拉取远端可用模型并与库内 diff。
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmModel:list')")
    @GetMapping("/vendor/{vendorId}/discover-models")
    public R<AiLlmDiscoverModelsResult> discoverModels(
            @PathVariable Long vendorId,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        return ok(modelDiscoveryService.discoverModels(vendorId, refresh));
    }

    /**
     * 批量勾选同步模型入库。
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmModel:add')")
    @Log(title = "AI 模型同步", businessType = BusinessType.INSERT)
    @PostMapping("/vendor/{vendorId}/sync-models")
    public R<Integer> syncModels(@PathVariable Long vendorId, @RequestBody AiLlmSyncModelsParams params) {
        return ok(modelDiscoveryService.syncModels(vendorId, params));
    }
}
