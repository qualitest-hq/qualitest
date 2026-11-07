package com.qualitest.flow.context;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.support.TestProjectAssetSupport;
import com.qualitest.project.support.TestProjectVariableEntrySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTTP 节点 extract 写入 {@code scope=asset} 后的项目素材落盘。
 * <p>
 * 抽取本身先改 Run 内存里的 asset 副本；本服务再按行上的 {@code persist} 决定是否合并回
 * 项目 {@code asset_variables}。{@code persist} 未写或为 true 时回写；为 false / 0 / no 时只保留本 Run 内存，不改项目库。
 * 落盘失败只记日志，不打断当前 Run。
 */
@Service
public class AssetExtractPersistService {

    private static final Logger log = LoggerFactory.getLogger(AssetExtractPersistService.class);

    private final TestProjectMapper testProjectMapper;

    public AssetExtractPersistService(TestProjectMapper testProjectMapper) {
        this.testProjectMapper = testProjectMapper;
    }

    /**
     * 根据 extracts 配置与已应用行，收集需要回写的 asset entryKey，合并内存值后更新项目素材。
     * 优先读配置里的 entryKey；配置未带时从已应用行回退。
     *
     * @param ctx             当前 Run 上下文（含内存 asset）
     * @param extractsConfig  节点上的 extracts 原始配置
     * @param applied         本步已写入内存的抽取结果行
     */
    public void persistFromExtractConfig(
            FlowRunContext ctx, JSONArray extractsConfig, List<JSONObject> applied) {
        if (ctx == null || ctx.getTestProjectId() == null) {
            return;
        }
        Set<String> entryKeys = collectEntryKeysFromConfig(extractsConfig);
        if (entryKeys.isEmpty()) {
            entryKeys = collectEntryKeysFromApplied(applied);
        }
        if (entryKeys.isEmpty()) {
            return;
        }
        try {
            mergeAndPersist(ctx.getTestProjectId(), ctx.getAsset(), entryKeys);
        } catch (Exception e) {
            log.warn("落盘 asset extract 失败 projectId={}: {}", ctx.getTestProjectId(), e.getMessage());
        }
    }

    /** 从节点 extracts 配置收集需回写项目库的 asset entryKey */
    private static Set<String> collectEntryKeysFromConfig(JSONArray extractsConfig) {
        Set<String> entryKeys = new LinkedHashSet<>();
        if (extractsConfig == null) {
            return entryKeys;
        }
        for (int i = 0; i < extractsConfig.size(); i++) {
            JSONObject row = extractsConfig.getJSONObject(i);
            if (row == null) {
                continue;
            }
            if (!"asset".equalsIgnoreCase(StrUtil.blankToDefault(row.getString("scope"), ""))) {
                continue;
            }
            // persist 缺省视为 true；false 时跳过落盘，只保留 Run 内存
            if (!shouldPersistAssetExtract(row)) {
                continue;
            }
            String entryKey = StrUtil.trimToNull(row.getString("entryKey"));
            if (entryKey != null) {
                entryKeys.add(entryKey);
            }
        }
        return entryKeys;
    }

    /** 从已应用抽取行收集需回写的 asset entryKey（配置缺 entryKey 时的回退） */
    private static Set<String> collectEntryKeysFromApplied(List<JSONObject> appliedExtracts) {
        Set<String> entryKeys = new LinkedHashSet<>();
        if (appliedExtracts == null || appliedExtracts.isEmpty()) {
            return entryKeys;
        }
        for (JSONObject row : appliedExtracts) {
            if (row == null) {
                continue;
            }
            if (!"asset".equalsIgnoreCase(StrUtil.blankToDefault(row.getString("scope"), ""))) {
                continue;
            }
            if (!shouldPersistAssetExtract(row)) {
                continue;
            }
            String entryKey = StrUtil.trimToNull(row.getString("entryKey"));
            if (entryKey != null) {
                entryKeys.add(entryKey);
            }
        }
        return entryKeys;
    }

    /**
     * 判断一条 extract 是否要把 asset 结果写回项目素材库。
     * 未出现 {@code persist} 字段、或值为空时视为 true；
     * 布尔 false，或字符串 {@code false}/{@code 0}/{@code no}（忽略大小写）视为不落盘。
     */
    static boolean shouldPersistAssetExtract(JSONObject row) {
        if (row == null) {
            return true;
        }
        if (!row.containsKey("persist")) {
            return true;
        }
        Object raw = row.get("persist");
        if (raw == null) {
            return true;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String text = StrUtil.trimToEmpty(String.valueOf(raw));
        if (text.isEmpty()) {
            return true;
        }
        return !"false".equalsIgnoreCase(text) && !"0".equals(text) && !"no".equalsIgnoreCase(text);
    }

    /**
     * 把 Run 内存 asset 中指定 entryKey 的字段浅合并进项目 asset_variables 并 update。
     * 已有条目保留未覆盖字段；没有则新建素材条目。
     */
    @SuppressWarnings("unchecked")
    void mergeAndPersist(Long testProjectId, Map<String, Object> assetScope, Set<String> entryKeys) {
        if (assetScope == null || entryKeys.isEmpty()) {
            return;
        }
        String json = testProjectMapper.selectAssetVariablesByTestProjectId(testProjectId);
        List<TestProjectAsset> entries = new ArrayList<>(TestProjectAssetSupport.parseEntries(json));
        boolean changed = false;
        for (String entryKey : entryKeys) {
            Object value = assetScope.get(entryKey);
            if (!(value instanceof Map<?, ?> fieldMap) || fieldMap.isEmpty()) {
                continue;
            }
            TestProjectAsset existing = TestProjectVariableEntrySupport.findByKey(entries, entryKey);
            Map<String, Object> inner = new LinkedHashMap<>();
            if (existing != null && existing.getAssets() != null) {
                Object prev = existing.getAssets().get(entryKey);
                if (prev instanceof Map<?, ?> prevMap) {
                    inner.putAll((Map<String, Object>) prevMap);
                }
            }
            for (Map.Entry<?, ?> e : fieldMap.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                inner.put(String.valueOf(e.getKey()), e.getValue());
            }
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put(entryKey, inner);
            if (existing == null) {
                entries.add(TestProjectAsset.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .key(entryKey)
                        .remark(null)
                        .updateTime(TestProjectVariableEntrySupport.nowUpdateTime())
                        .assets(wrapper)
                        .build());
            } else {
                existing.setAssets(wrapper);
                existing.setUpdateTime(TestProjectVariableEntrySupport.nowUpdateTime());
            }
            changed = true;
        }
        if (!changed) {
            return;
        }
        TestProject row = new TestProject();
        row.setTestProjectId(testProjectId);
        row.setAssetVariables(TestProjectAssetSupport.toJson(
                TestProjectVariableEntrySupport.normalizeEntriesForPersist(entries)));
        testProjectMapper.updateAssetVariables(row);
    }
}
