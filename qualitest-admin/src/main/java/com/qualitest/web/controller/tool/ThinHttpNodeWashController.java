package com.qualitest.web.controller.tool;

import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.flow.migrate.ThinHttpNodeWashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 厚 HTTP 节点洗库维护接口。
 * <p>
 * <b>执行前必须备份 test_flow 表。</b>
 * 默认 apply=false：只扫描出报告，不改库；apply=true 才会写回 graph_json。
 */
@Tag(name = "维护工具-薄节点洗库")
@RestController
@RequestMapping("/tool/thinHttpNodeWash")
@RequiredArgsConstructor
public class ThinHttpNodeWashController extends BaseController {

    private final ThinHttpNodeWashService thinHttpNodeWashService;

    /**
     * 扫描或洗 test_flow.graph_json 里的 project HTTP 厚节点。
     *
     * @param apply         false=只报告；true=写库（须已备份）
     * @param testProjectId 可选，只处理指定项目
     * @param sampleLimit   报告抽样条数，默认 20
     */
    @Operation(summary = "薄节点洗库（默认 dry-run）")
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @PostMapping
    public R<ThinHttpNodeWashService.WashReport> wash(
            @RequestParam(value = "apply", defaultValue = "false") boolean apply,
            @RequestParam(value = "testProjectId", required = false) Long testProjectId,
            @RequestParam(value = "sampleLimit", defaultValue = "20") int sampleLimit) {
        return R.ok(thinHttpNodeWashService.wash(apply, testProjectId, sampleLimit));
    }
}
