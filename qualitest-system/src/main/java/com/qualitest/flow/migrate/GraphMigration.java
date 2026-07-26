package com.qualitest.flow.migrate;

import com.qualitest.flow.model.GraphJson;

import java.util.List;

/**
 * 单步图 schema 迁移：{@code sourceVersion} → {@code targetVersion}。
 */
public interface GraphMigration {

    int sourceVersion();

    int targetVersion();

    /**
     * 将图从 {@link #sourceVersion()} 迁移到 {@link #targetVersion()}。
     * 调用方传入副本，本方法可原地修改。
     *
     * @param graph   待迁移图
     * @param summary 收集人类可读的变更摘要（可为 null）
     */
    void migrate(GraphJson graph, List<String> summary);
}
