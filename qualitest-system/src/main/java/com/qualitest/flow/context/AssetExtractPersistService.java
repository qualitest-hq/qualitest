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
 * HTTP extract scope=asset 成功后，把 Run 内存中的素材合并回项目 asset_variables。
 */
@Service
public class AssetExtractPersistService {

    private static final Logger log = LoggerFactory.getLogger(AssetExtractPersistService.class);

    private final TestProjectMapper testProjectMapper;

    public AssetExtractPersistService(TestProjectMapper testProjectMapper) {
        this.testProjectMapper = testProjectMapper;
    }

    /**
     * 按 extracts 配置收集 scope=asset 的 entryKey 并落盘；配置未带 entryKey 时从 applied 行回退。
     * 失败只打日志，不打断 Run。
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
            String entryKey = StrUtil.trimToNull(row.getString("entryKey"));
            if (entryKey != null) {
                entryKeys.add(entryKey);
            }
        }
        return entryKeys;
    }

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
            String entryKey = StrUtil.trimToNull(row.getString("entryKey"));
            if (entryKey != null) {
                entryKeys.add(entryKey);
            }
        }
        return entryKeys;
    }

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
