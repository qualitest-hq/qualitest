package com.qualitest.web.controller.api;

import cn.hutool.json.JSONUtil;
import com.qualitest.common.annotation.Anonymous;
import com.qualitest.common.core.controller.ProjectController;
import com.qualitest.common.core.domain.R;
import com.qualitest.api.params.ApiImportParams;
import com.qualitest.api.result.ApiImportResult;
import com.qualitest.api.service.IApiImportService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试项目API接口信息 Controller
 *
 * @author qualitest
 */
@Slf4j
@RestController
@RequestMapping("/api/project")
@AllArgsConstructor
@Anonymous
public class ProjectApiController extends ProjectController {

    private final IApiImportService apiImportService;

    /**
     * 接收外部上传的 API 列表并批量导入。
     * 项目 id 由请求头项目令牌解析。
     * 返回体含新增/更新/失败统计与逐条明细；
     * 若有接口被更新，还含 syncImpact（可能受影响的测试流汇总）。
     */
    @PostMapping("/importApis")
    public R<ApiImportResult> importApis(@Valid @RequestBody ApiImportParams params) {

        Long projectId = getTestProjectId();
        Long userId = getUserId();

        log.info("开始导入接口, projectId={}, userId={}, apiCount={}", projectId, userId, params.getApiList().size());
        log.debug("接口列表: {}", JSONUtil.toJsonPrettyStr(params.getApiList()));

        // 执行导入
        ApiImportResult result = apiImportService.importApis(projectId, userId, params);

        log.info("接口导入完成, projectId={}, 提交={}, 处理={}, 新增={}, 更新={}, 成功={}, 失败={}",
                projectId, params.getApiList().size(), result.getTotalCount(),
                result.getInsertCount(), result.getUpdateCount(),
                result.getSuccessCount(), result.getFailCount());

        return ok(result);
    }

}
