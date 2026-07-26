package com.qualitest.flow.migrate;

import com.alibaba.fastjson2.JSON;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试流 graph_json 版本检测与链式迁移。
 * <p>
 * 运行快照（{@code test_flow_run.graph_json_snapshot}）不经过本类；子流 pinned 图仅在运行期内存 normalize。
 */
@Component
public class GraphMigrator {

    private static final List<GraphMigration> MIGRATIONS = List.of();

    /**
     * 读取图当前 schema 版本；缺失视为 {@link GraphSchemaVersions#DEFAULT}（当前为 1）。
     */
    public int resolveVersion(GraphJson graph) {
        if (graph == null || graph.getMeta() == null || graph.getMeta().getSchemaVersion() == null) {
            return GraphSchemaVersions.DEFAULT;
        }
        return graph.getMeta().getSchemaVersion();
    }

    /**
     * 图是否低于当前目标版本。
     */
    public boolean needsUpgrade(GraphJson graph) {
        return resolveVersion(graph) < GraphSchemaVersions.CURRENT;
    }

    /**
     * 检测升级可用性与变更摘要（不写库）。
     */
    public GraphUpgradePreview previewUpgrade(GraphJson graph) {
        int fromVersion = resolveVersion(graph);
        if (fromVersion >= GraphSchemaVersions.CURRENT) {
            return GraphUpgradePreview.builder()
                    .upgradeAvailable(false)
                    .upgradeFromVersion(fromVersion)
                    .upgradeToVersion(GraphSchemaVersions.CURRENT)
                    .upgradeSummary(List.of())
                    .build();
        }
        List<String> summary = new ArrayList<>();
        migrateChain(copy(graph), summary);
        if (!summary.isEmpty()) {
            summary.add("升级后新 Run 的图指纹将与历史 Run 不可直接对比");
        }
        return GraphUpgradePreview.builder()
                .upgradeAvailable(true)
                .upgradeFromVersion(fromVersion)
                .upgradeToVersion(GraphSchemaVersions.CURRENT)
                .upgradeSummary(summary)
                .build();
    }

    /**
     * 将图迁移到当前最新版本（深拷贝后迁移，不修改入参）。
     */
    public GraphJson migrateToLatest(GraphJson graph) {
        if (graph == null) {
            return GraphJson.builder().build();
        }
        if (!needsUpgrade(graph)) {
            return copy(graph);
        }
        GraphJson copy = copy(graph);
        migrateChain(copy, null);
        return copy;
    }

    /**
     * 新保存图时写入当前 schema 版本（不执行完整迁移链）。
     */
    public void stampCurrentVersion(GraphJson graph) {
        if (graph == null) {
            return;
        }
        GraphMeta meta = graph.getMeta();
        if (meta == null) {
            meta = new GraphMeta();
            graph.setMeta(meta);
        }
        meta.setSchemaVersion(GraphSchemaVersions.CURRENT);
    }

    private void migrateChain(GraphJson graph, List<String> summary) {
        while (resolveVersion(graph) < GraphSchemaVersions.CURRENT) {
            int version = resolveVersion(graph);
            GraphMigration migration = findMigrationForSource(version);
            if (migration == null) {
                break;
            }
            migration.migrate(graph, summary);
        }
    }

    private GraphMigration findMigrationForSource(int sourceVersion) {
        for (GraphMigration migration : MIGRATIONS) {
            if (migration.sourceVersion() == sourceVersion) {
                return migration;
            }
        }
        return null;
    }

    private GraphJson copy(GraphJson graph) {
        return JSON.parseObject(JSON.toJSONString(graph), GraphJson.class);
    }
}
