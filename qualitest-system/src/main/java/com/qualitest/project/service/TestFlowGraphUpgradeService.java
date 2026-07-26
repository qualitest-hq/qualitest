package com.qualitest.project.service;

import com.qualitest.flow.migrate.GraphMigrator;
import com.qualitest.flow.migrate.GraphUpgradePreview;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.common.exception.ServiceException;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 测试流 graph_json schema 升级：检测、预览与持久化。
 * <p>
 * 运行快照（{@code test_flow_run.graph_json_snapshot}）不经过本服务。
 */
@Service
@RequiredArgsConstructor
public class TestFlowGraphUpgradeService {

    private final GraphMigrator graphMigrator;
    private final GraphJsonValidator graphJsonValidator;

    /**
     * 根据 graphJson 填充 TestFlowResult 上的升级元数据字段。
     */
    public void enrichUpgradeMeta(TestFlowResult result) {
        if (result == null) {
            return;
        }
        if (StrUtil.isBlank(result.getGraphJson())) {
            result.setUpgradeAvailable(false);
            return;
        }
        try {
            GraphJson graph = GraphJson.parse(result.getGraphJson());
            applyPreview(result, graphMigrator.previewUpgrade(graph));
        } catch (Exception e) {
            result.setUpgradeAvailable(false);
        }
    }

    /**
     * 将测试流图迁移到最新 schema 并写回 test_flow.graph_json。
     */
    public TestFlowResult upgradeGraph(TestFlow testFlow, ITestFlowService testFlowService) {
        if (testFlow == null || testFlow.getTestFlowId() == null) {
            throw new ServiceException("testFlowId 不能为空");
        }
        if (StrUtil.isBlank(testFlow.getGraphJson())) {
            throw new ServiceException("测试流 graph_json 为空");
        }
        GraphJson graph;
        try {
            graph = GraphJson.parse(testFlow.getGraphJson());
        } catch (Exception e) {
            throw new ServiceException("图解析失败: " + e.getMessage());
        }
        if (!graphMigrator.needsUpgrade(graph)) {
            TestFlowResult current = testFlowService.selectTestFlowResult(testFlow.getTestFlowId());
            enrichUpgradeMeta(current);
            return current;
        }
        GraphJson migrated = graphMigrator.migrateToLatest(graph);
        GraphValidationResult validation = graphJsonValidator.validate(migrated);
        if (!validation.isOk()) {
            throw new ServiceException("升级后图校验失败: " + String.join("; ", validation.getErrors()));
        }
        TestFlow update = new TestFlow();
        update.setTestFlowId(testFlow.getTestFlowId());
        update.setGraphJson(migrated.toJsonString());
        testFlowService.updateTestFlow(update);

        TestFlowResult result = testFlowService.selectTestFlowResult(testFlow.getTestFlowId());
        enrichUpgradeMeta(result);
        return result;
    }

    private void applyPreview(TestFlowResult result, GraphUpgradePreview preview) {
        result.setUpgradeAvailable(preview.isUpgradeAvailable());
        result.setUpgradeFromVersion(preview.getUpgradeFromVersion());
        result.setUpgradeToVersion(preview.getUpgradeToVersion());
        result.setUpgradeSummary(preview.getUpgradeSummary());
    }
}
