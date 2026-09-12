package com.qualitest.ai.tools.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 素材库写入相关静态方法：按 key 查询、解析 fields、写库 insert/update、提取字段名。
 * persistAsset 供全自动 upsert 直写与聊天侧确认落盘共用。
 */
public final class AssetUpsertSupport {

    private AssetUpsertSupport() {
    }

    /**
     * 按项目 id 与素材 key 查询一条素材。
     * 查不到、或服务抛出业务「不存在」类异常时返回 null，不向外抛。
     */
    public static TestProjectAssetResult findByKeyOrNull(ITestProjectAssetService service,
                                                        Long projectId,
                                                        String key) {
        if (service == null || projectId == null || key == null || key.isBlank()) {
            return null;
        }
        try {
            return service.selectTestProjectAssetResultByKey(projectId, key);
        } catch (ServiceException ex) {
            return null;
        }
    }

    /**
     * 把提案字段写入项目素材库。
     * 当前库中无该 key 则新增，已有则按 id 更新；备注为空时更新保留原备注。
     */
    public static void persistAsset(ITestProjectAssetService service,
                                    Long projectId,
                                    String key,
                                    Map<String, Object> fields,
                                    String remark) {
        if (service == null) {
            throw new ServiceException("素材服务未就绪");
        }
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put(key, fields);
        com.qualitest.project.params.TestProjectAssetSaveParams.TestProjectAssetSaveParamsBuilder params =
                com.qualitest.project.params.TestProjectAssetSaveParams.builder()
                        .testProjectId(projectId)
                        .key(key)
                        .assets(assets);

        TestProjectAssetResult existing = findByKeyOrNull(service, projectId, key);
        if (existing == null) {
            service.insertTestProjectAsset(params.remark(remark).build());
        } else {
            service.updateTestProjectAsset(params
                    .id(existing.getId())
                    .remark(remark != null ? remark : existing.getRemark())
                    .build());
        }
    }

    /**
     * 把工具入参或提案里的 fields 转成「字段名 → 值」扁平 Map。
     * 不是 Map、为空、或没有有效字段名时返回 null。
     */
    public static Map<String, Object> parseFlatFields(Object fieldsObj) {
        if (!(fieldsObj instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String name = String.valueOf(e.getKey()).trim();
            if (name.isEmpty()) {
                continue;
            }
            fields.put(name, e.getValue());
        }
        return fields.isEmpty() ? null : fields;
    }

    /**
     * 从提案 JSON 取出字段名列表（不含值）。
     * 有 fields 对象时用其键名；否则用已存的 fieldNames 数组（列表摘要场景）。
     */
    public static List<String> extractFieldNames(JSONObject proposalJson) {
        if (proposalJson == null) {
            return List.of();
        }
        Map<String, Object> fields = parseFlatFields(proposalJson.get("fields"));
        if (fields != null) {
            return new ArrayList<>(fields.keySet());
        }
        return fieldNamesFromArray(proposalJson.getJSONArray("fieldNames"));
    }

    /**
     * 从 fields 对象收集字段名，装成 JSON 数组。
     * 用于会话列表摘要：去掉明文值，只保留字段名。
     */
    public static JSONArray fieldNamesArrayFromFieldsObj(Object fieldsObj) {
        JSONArray fieldNames = new JSONArray();
        Map<String, Object> fields = parseFlatFields(fieldsObj);
        if (fields != null) {
            fieldNames.addAll(fields.keySet());
        }
        return fieldNames;
    }

    /** 把 JSON 数组形式的字段名列表转成 Java List，跳过空串 */
    private static List<String> fieldNamesFromArray(JSONArray fieldNames) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            String name = fieldNames.getString(i);
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        return names;
    }
}
