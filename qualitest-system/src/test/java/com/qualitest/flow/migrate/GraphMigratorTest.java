package com.qualitest.flow.migrate;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link GraphMigrator} 单元测试。
 */
class GraphMigratorTest {

    private GraphMigrator migrator;

    @BeforeEach
    void setUp() {
        migrator = new GraphMigrator();
    }

    @Test
    void resolveVersion_missingMeta_defaultsToV1() {
        GraphJson graph = GraphJson.builder().build();
        assertEquals(GraphSchemaVersions.DEFAULT, migrator.resolveVersion(graph));
        assertEquals(GraphSchemaVersions.V1, migrator.resolveVersion(graph));
    }

    @Test
    void needsUpgrade_missingSchemaVersion_returnsFalse() {
        assertFalse(migrator.needsUpgrade(minimalGraph()));
    }

    @Test
    void migrateToLatest_v1Graph_isNoOp() {
        GraphJson graph = v1Graph();
        GraphJson migrated = migrator.migrateToLatest(graph);
        assertEquals(GraphSchemaVersions.V1, migrator.resolveVersion(migrated));
        assertEquals("sc-1", migrated.getMeta().getActiveScenarioId());
    }

    @Test
    void previewUpgrade_v1Graph_notAvailable() {
        GraphUpgradePreview preview = migrator.previewUpgrade(v1Graph());
        assertFalse(preview.isUpgradeAvailable());
        assertEquals(GraphSchemaVersions.V1, preview.getUpgradeFromVersion());
        assertEquals(GraphSchemaVersions.CURRENT, preview.getUpgradeToVersion());
    }

    @Test
    void stampCurrentVersion_writesSchemaVersion() {
        GraphJson graph = minimalGraph();
        migrator.stampCurrentVersion(graph);
        assertEquals(GraphSchemaVersions.CURRENT, graph.getMeta().getSchemaVersion());
    }

    private static GraphJson minimalGraph() {
        GraphNode node = GraphNode.builder()
                .id("n1")
                .type("http")
                .position(GraphNodePosition.builder().x(0).y(0).build())
                .data(Map.of("name", "HTTP", "callMode", "project"))
                .build();
        return GraphJson.builder()
                .nodes(new ArrayList<>(List.of(node)))
                .edges(new ArrayList<>())
                .build();
    }

    private static GraphJson v1Graph() {
        GraphJson graph = minimalGraph();
        GraphMeta meta = new GraphMeta();
        meta.setSchemaVersion(GraphSchemaVersions.V1);
        meta.setActiveScenarioId("sc-1");
        graph.setMeta(meta);
        return graph;
    }
}
