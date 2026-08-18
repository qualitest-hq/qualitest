package com.qualitest.project.support;

import cn.hutool.core.util.StrUtil;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestProjectApiGroup;
import com.qualitest.project.service.ITestProjectApiGroupService;

import java.util.List;
import java.util.Map;

/**
 * 按父分组和名称查找或创建 API 分组。
 * 分组名带点号时逐级创建，如 系统.登录。
 */
public final class TestProjectApiGroupResolveSupport {

    private TestProjectApiGroupResolveSupport() {}

    /**
     * 解析分组 id：空名用「默认分组」；单级直接查找或创建；多级按点号拆开逐级创建并回写 ancestors。
     */
    public static Long resolve(
            ITestProjectApiGroupService groupService,
            Long projectId,
            String apiGroup,
            Map<String, Long> cache) {
        if (StrUtil.isBlank(apiGroup)) {
            return findOrCreate(groupService, projectId, "默认分组", 0L, cache);
        }
        if (!apiGroup.contains(".")) {
            return findOrCreate(groupService, projectId, apiGroup.trim(), 0L, cache);
        }
        String[] levels = apiGroup.split("\\.");
        Long parentId = 0L;
        String ancestors = "";
        for (int i = 0; i < levels.length; i++) {
            String groupName = levels[i].trim();
            if (StrUtil.isBlank(groupName)) {
                continue;
            }
            Long groupId = findOrCreate(groupService, projectId, groupName, parentId, cache);
            ancestors = i == 0 ? String.valueOf(groupId) : ancestors + "," + groupId;
            parentId = groupId;
            TestProjectApiGroup group = groupService.selectTestProjectApiGroupById(groupId);
            if (group != null && !ancestors.equals(group.getAncestors())) {
                group.setAncestors(ancestors);
                group.setUpdateTime(DateUtils.getNowDate());
                groupService.updateTestProjectApiGroup(group);
            }
        }
        return parentId;
    }

    /** 同一父分组下按名称查找，没有则插入一条。 */
    private static Long findOrCreate(
            ITestProjectApiGroupService groupService,
            Long projectId,
            String groupName,
            Long parentId,
            Map<String, Long> cache) {
        String cacheKey = parentId + ":" + groupName;
        Long cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<TestProjectApiGroup> groupList = groupService.selectTestProjectApiGroupList(
                TestProjectApiGroup.builder()
                        .testProjectId(projectId)
                        .parentId(parentId)
                        .groupName(groupName)
                        .delStatus(0)
                        .build());
        if (groupList != null && !groupList.isEmpty()) {
            Long groupId = groupList.get(0).getApiGroupId();
            cache.put(cacheKey, groupId);
            return groupId;
        }
        TestProjectApiGroup newGroup = TestProjectApiGroup.builder()
                .testProjectId(projectId)
                .parentId(parentId)
                .groupName(groupName)
                .sortNum(0)
                .delStatus(0)
                .createTime(DateUtils.getNowDate())
                .build();
        groupService.insertTestProjectApiGroup(newGroup);
        cache.put(cacheKey, newGroup.getApiGroupId());
        return newGroup.getApiGroupId();
    }
}
