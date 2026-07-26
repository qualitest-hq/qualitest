package com.qualitest.web.controller.tool;

import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.flow.migrate.ApiHealthBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试流 api_health_* 存量回填 HTTP 入口。
 * <p>
 * 默认 dry-run（apply=false）只返回扫描报告；apply=true 才更新数据库。
 */
@Tag(name = "维护工具-API健康回填")
@RestController
@RequestMapping("/tool/apiHealthBackfill")
@RequiredArgsConstructor
public class ApiHealthBackfillController extends BaseController {

    private final ApiHealthBackfillService apiHealthBackfillService;

    /**
     * 扫描或回填测试流的 API 语义健康字段。
     *
     * @param apply         false=只报告不写库；true=写库
     * @param testProjectId 可选，只处理该项目下的流
     * @param sampleLimit   报告里告警流抽样条数，默认 20
     * @return 扫描/回填报告
     */
    @Operation(summary = "API健康字段回填（默认 dry-run）")
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @PostMapping
    public R<ApiHealthBackfillService.BackfillReport> backfill(
            @RequestParam(value = "apply", defaultValue = "false") boolean apply,
            @RequestParam(value = "testProjectId", required = false) Long testProjectId,
            @RequestParam(value = "sampleLimit", defaultValue = "20") int sampleLimit) {
        return R.ok(apiHealthBackfillService.backfill(apply, testProjectId, sampleLimit));
    }
}
