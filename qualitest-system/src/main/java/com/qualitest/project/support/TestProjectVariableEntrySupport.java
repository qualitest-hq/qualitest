package com.qualitest.project.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.constant.TestProjectConstants;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.params.TestProjectAssetSaveParams;

import java.util.*;

/**
 * 变量条目 JSON 数组公共工具（素材库 asset_variables、环境 env_variables 共用）
 *
 * @author qualitest
 */
public final class TestProjectVariableEntrySupport {

    private TestProjectVariableEntrySupport() {
    }

    /**
     * 将变量条目 JSON 文本解析为列表（读库场景：条目须含 id 与 key）。
     */
    public static List<TestProjectAsset> parseEntries(String variablesJson) {
        return parseEntries(variablesJson, true);
    }

    /**
     * 解析变量条目 JSON。
     *
     * @param variablesJson 原始 JSON 文本
     * @param requireId     true 时条目缺少 id 抛错；false 时允许 id 为空（保存前由 normalize 补全）
     */
    public static List<TestProjectAsset> parseEntries(String variablesJson, boolean requireId) {
        String json = StrUtil.blankToDefault(variablesJson, TestProjectConstants.EMPTY_VARIABLE_ENTRIES_JSON);
        JSONArray array;
        try {
            array = JSON.parseArray(json);
        } catch (Exception e) {
            throw new ServiceException("变量条目数据格式错误");
        }
        if (array == null) {
            throw new ServiceException("变量条目根节点必须为 JSON 数组");
        }
        List<TestProjectAsset> entries = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj == null) {
                throw new ServiceException("变量条目不能为空");
            }
            Long id = obj.getLong("id");
            String key = obj.getString("key");
            if (requireId && id == null) {
                throw new ServiceException("变量条目缺少 id");
            }
            if (StrUtil.isBlank(key)) {
                throw new ServiceException("变量条目缺少 key");
            }
            JSONObject assetsObj = obj.getJSONObject("assets");
            Map<String, Object> assets = assetsObj == null ? new LinkedHashMap<>() : new LinkedHashMap<>(assetsObj);
            entries.add(TestProjectAsset.builder()
                    .id(id)
                    .key(key.trim())
                    .remark(obj.getString("remark"))
                    .updateTime(obj.getString("updateTime"))
                    .assets(assets)
                    .build());
        }
        return entries;
    }

    /**
     * 将条目列表序列化为 JSON 数组字符串。
     */
    public static String toJson(List<TestProjectAsset> entries) {
        return JSON.toJSONString(entries == null ? Collections.emptyList() : entries);
    }

    public static String nowUpdateTime() {
        return DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, DateUtils.getNowDate());
    }

    /**
     * 校验 assets 包装层：有且仅有一个与 key 同名的子键。
     */
    public static void validateAssetsWrapper(String key, Map<String, Object> assets) {
        if (StrUtil.isBlank(key)) {
            throw new ServiceException("变量 key 不能为空");
        }
        if (assets == null || assets.isEmpty()) {
            throw new ServiceException("assets 结构非法：须包含与 key 同名的子键");
        }
        if (assets.size() != 1 || !assets.containsKey(key)) {
            throw new ServiceException("assets 结构非法：须为 { \"" + key + "\": <value> } 且仅一个子键");
        }
    }

    /**
     * 断言列表内 key 全局唯一（用于环境整包保存）。
     */
    public static void assertAllKeysUnique(List<TestProjectAsset> entries) {
        Set<String> seen = new HashSet<>();
        for (TestProjectAsset entry : entries) {
            String key = entry.getKey();
            if (!seen.add(key)) {
                throw new ServiceException("变量 key 重复：" + key);
            }
        }
    }

    /**
     * 断言列表内 key 唯一（修改单条时排除自身 id）。
     */
    public static void assertKeyUnique(List<TestProjectAsset> entries, String key, Long excludeId) {
        for (TestProjectAsset entry : entries) {
            if (key.equals(entry.getKey()) && (excludeId == null || !excludeId.equals(entry.getId()))) {
                throw new ServiceException("变量 key 已存在：" + key);
            }
        }
    }

    /**
     * 保存前规范化：补 id、刷新 updateTime、校验 key 与 assets 包装、保证 key 唯一。
     */
    public static List<TestProjectAsset> normalizeEntriesForPersist(List<TestProjectAsset> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        List<TestProjectAsset> normalized = new ArrayList<>(entries.size());
        String now = nowUpdateTime();
        for (TestProjectAsset entry : entries) {
            String key = entry.getKey() == null ? null : entry.getKey().trim();
            if (StrUtil.isBlank(key)) {
                throw new ServiceException("变量 key 不能为空");
            }
            Map<String, Object> assets = normalizeAssetsMap(entry.getAssets());
            validateAssetsWrapper(key, assets);
            Long id = entry.getId() != null ? entry.getId() : IdUtil.getSnowflakeNextId();
            normalized.add(TestProjectAsset.builder()
                    .id(id)
                    .key(key)
                    .remark(entry.getRemark())
                    .updateTime(now)
                    .assets(assets)
                    .build());
        }
        assertAllKeysUnique(normalized);
        return normalized;
    }

    /**
     * 将客户端提交的 env_variables JSON 解析并规范化为可入库文本。
     */
    public static String normalizeVariablesJsonForPersist(String variablesJson) {
        List<TestProjectAsset> entries = parseEntries(variablesJson, false);
        return toJson(normalizeEntriesForPersist(entries));
    }

    public static TestProjectAsset buildNewEntry(TestProjectAssetSaveParams params, Long id) {
        String key = params.getKey();
        Map<String, Object> assets = normalizeAssetsMap(params.getAssets());
        return TestProjectAsset.builder()
                .id(id)
                .key(key)
                .remark(params.getRemark())
                .updateTime(nowUpdateTime())
                .assets(assets)
                .build();
    }

    public static void applyUpdate(TestProjectAsset existing, TestProjectAssetSaveParams params) {
        String newKey = params.getKey();
        Map<String, Object> assets = normalizeAssetsMap(params.getAssets());
        existing.setKey(newKey);
        existing.setRemark(params.getRemark());
        existing.setAssets(assets);
        existing.setUpdateTime(nowUpdateTime());
    }

    public static TestProjectAsset findById(List<TestProjectAsset> entries, Long id) {
        if (id == null) {
            return null;
        }
        for (TestProjectAsset entry : entries) {
            if (id.equals(entry.getId())) {
                return entry;
            }
        }
        return null;
    }

    public static TestProjectAsset findByKey(List<TestProjectAsset> entries, String key) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        for (TestProjectAsset entry : entries) {
            if (key.equals(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    static Map<String, Object> normalizeAssetsMap(Map<String, Object> assets) {
        if (assets == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>(assets);
        for (Map.Entry<String, Object> e : copy.entrySet()) {
            Object v = e.getValue();
            if (v instanceof JSONObject jsonObject) {
                copy.put(e.getKey(), new LinkedHashMap<>(jsonObject));
            }
        }
        return copy;
    }

}
