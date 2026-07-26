package com.qualitest.project.support;

import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.params.TestProjectAssetParams;
import com.qualitest.project.params.TestProjectAssetSaveParams;
import com.qualitest.project.result.TestProjectAssetResult;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目素材库工具类（变量条目公共逻辑见 {@link TestProjectVariableEntrySupport}）
 *
 * @author qualitest
 */
public final class TestProjectAssetSupport {

    private TestProjectAssetSupport() {
    }

    public static List<TestProjectAsset> parseEntries(String assetVariablesJson) {
        return TestProjectVariableEntrySupport.parseEntries(assetVariablesJson);
    }

    public static String toJson(List<TestProjectAsset> entries) {
        return TestProjectVariableEntrySupport.toJson(entries);
    }

    public static String nowUpdateTime() {
        return TestProjectVariableEntrySupport.nowUpdateTime();
    }

    public static void validateAssetsWrapper(String key, java.util.Map<String, Object> assets) {
        TestProjectVariableEntrySupport.validateAssetsWrapper(key, assets);
    }

    public static void validateSaveParams(TestProjectAssetSaveParams params, boolean isUpdate) {
        if (params == null || params.getTestProjectId() == null) {
            throw new ServiceException("请指定测试项目");
        }
        if (StrUtil.isBlank(params.getKey())) {
            throw new ServiceException("素材 key 不能为空");
        }
        params.setKey(params.getKey().trim());
        if (isUpdate && params.getId() == null) {
            throw new ServiceException("修改素材须指定条目 id");
        }
        if (!isUpdate && params.getId() != null) {
            throw new ServiceException("新增素材不应携带 id");
        }
        validateAssetsWrapper(params.getKey(), params.getAssets());
    }

    public static void assertKeyUnique(List<TestProjectAsset> entries, String key, Long excludeId) {
        TestProjectVariableEntrySupport.assertKeyUnique(entries, key, excludeId);
    }

    public static TestProjectAsset findById(List<TestProjectAsset> entries, Long id) {
        return TestProjectVariableEntrySupport.findById(entries, id);
    }

    public static TestProjectAsset findByKey(List<TestProjectAsset> entries, String key) {
        return TestProjectVariableEntrySupport.findByKey(entries, key);
    }

    public static TestProjectAssetResult toResult(TestProjectAsset entry, Long testProjectId) {
        return TestProjectAssetResult.builder()
                .id(entry.getId())
                .testProjectId(testProjectId)
                .key(entry.getKey())
                .remark(entry.getRemark())
                .updateTime(entry.getUpdateTime())
                .assets(entry.getAssets())
                .build();
    }

    public static List<TestProjectAssetResult> filterAndSort(
            List<TestProjectAsset> entries,
            Long testProjectId,
            TestProjectAssetParams params) {
        return entries.stream()
                .filter(e -> matchFilter(e, params))
                .sorted(Comparator.comparing(
                        TestProjectAsset::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(e -> toResult(e, testProjectId))
                .collect(Collectors.toList());
    }

    private static boolean matchFilter(TestProjectAsset entry, TestProjectAssetParams params) {
        if (params == null) {
            return true;
        }
        if (StrUtil.isNotBlank(params.getKey())
                && (entry.getKey() == null || !entry.getKey().contains(params.getKey()))) {
            return false;
        }
        if (StrUtil.isNotBlank(params.getRemark())) {
            String remark = entry.getRemark();
            return remark != null && remark.contains(params.getRemark());
        }
        return true;
    }

    public static TestProjectAsset buildNewEntry(TestProjectAssetSaveParams params, Long id) {
        return TestProjectVariableEntrySupport.buildNewEntry(params, id);
    }

    public static void applyUpdate(TestProjectAsset existing, TestProjectAssetSaveParams params) {
        TestProjectVariableEntrySupport.applyUpdate(existing, params);
    }

}
