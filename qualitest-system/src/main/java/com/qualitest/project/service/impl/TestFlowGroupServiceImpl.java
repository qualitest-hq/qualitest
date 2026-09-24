package com.qualitest.project.service.impl;

import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestFlowGroup;
import com.qualitest.project.mapper.TestFlowGroupMapper;
import com.qualitest.project.params.TestFlowGroupParams;
import com.qualitest.project.result.TestFlowGroupResult;
import com.qualitest.project.service.ITestFlowGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 测试流分组Service业务层处理
 *
 * @author qualitest
 * @date 2026-09-24
 */
@Service
public class TestFlowGroupServiceImpl implements ITestFlowGroupService {

    @Autowired
    private TestFlowGroupMapper testFlowGroupMapper;

    /**
     * 查询测试流分组列表
     *
     * @param testFlowGroup 测试流分组
     * @return 测试流分组
     */
    @Override
    public List<TestFlowGroup> selectTestFlowGroupList(TestFlowGroup testFlowGroup) {
        return testFlowGroupMapper.selectTestFlowGroupList(testFlowGroup);
    }

    /**
     * 查询测试流分组
     *
     * @param flowGroupId 测试流分组主键
     * @return 测试流分组
     */
    @Override
    public TestFlowGroup selectTestFlowGroupById(Long flowGroupId) {
        return testFlowGroupMapper.selectTestFlowGroupById(flowGroupId);
    }

    /**
     * 查询测试流分组Result列表
     *
     * @param params 测试流分组Params
     * @return 测试流分组Result集合
     */
    @Override
    public List<TestFlowGroupResult> selectTestFlowGroupResultList(TestFlowGroupParams params) {
        return testFlowGroupMapper.selectTestFlowGroupResultList(params);
    }

    /**
     * 构建项目内分组树
     *
     * @param testProjectId 测试项目ID
     * @return 树根列表
     */
    @Override
    public List<TestFlowGroupResult> selectTestFlowGroupTree(Long testProjectId) {
        List<TestFlowGroupResult> flat = testFlowGroupMapper.selectTestFlowGroupResultList(
                TestFlowGroupParams.builder().testProjectId(testProjectId).build());
        return buildTree(flat);
    }

    /**
     * 将平铺分组列表组装为树
     *
     * @param flat 平铺列表
     * @return 根节点列表
     */
    private List<TestFlowGroupResult> buildTree(List<TestFlowGroupResult> flat) {
        Map<Long, TestFlowGroupResult> map = new HashMap<>();
        for (TestFlowGroupResult node : flat) {
            if (node.getLabel() == null) {
                node.setLabel(node.getGroupName());
            }
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            map.put(node.getFlowGroupId(), node);
        }
        List<TestFlowGroupResult> roots = new ArrayList<>();
        for (TestFlowGroupResult node : flat) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId == 0L || !map.containsKey(parentId)) {
                roots.add(node);
            } else {
                map.get(parentId).getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 获取测试流分组详细信息
     *
     * @param flowGroupId 测试流分组主键
     * @return 测试流分组Result
     */
    @Override
    public TestFlowGroupResult selectTestFlowGroupResult(Long flowGroupId) {
        return testFlowGroupMapper.selectTestFlowGroupResult(flowGroupId);
    }

    /**
     * 新增测试流分组
     *
     * @param testFlowGroup 测试流分组
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestFlowGroup(TestFlowGroup testFlowGroup) {
        if (testFlowGroup.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        if (StrUtil.isBlank(testFlowGroup.getGroupName())) {
            throw new ServiceException("分组名称不能为空");
        }
        Long parentId = testFlowGroup.getParentId() == null ? 0L : testFlowGroup.getParentId();
        testFlowGroup.setParentId(parentId);
        testFlowGroup.setAncestors(resolveAncestors(parentId, testFlowGroup.getTestProjectId()));
        if (testFlowGroup.getSortNum() == null) {
            testFlowGroup.setSortNum(0);
        }
        if (testFlowGroup.getDelStatus() == null) {
            testFlowGroup.setDelStatus(0);
        }
        testFlowGroup.setCreateTime(DateUtils.getNowDate());
        return testFlowGroupMapper.insertTestFlowGroup(testFlowGroup);
    }

    /**
     * 根据父分组计算新节点的祖级串。
     * 父为 0 时返回字符串 0；否则为父的 ancestors 再拼上父 id。
     *
     * @param parentId      父分组主键
     * @param testProjectId 项目主键（用于校验父属于本项目）
     * @return 祖级串
     */
    private String resolveAncestors(Long parentId, Long testProjectId) {
        if (parentId == null || parentId == 0L) {
            return "0";
        }
        TestFlowGroup parent = testFlowGroupMapper.selectTestFlowGroupById(parentId);
        if (parent == null || (parent.getDelStatus() != null && parent.getDelStatus() != 0)) {
            throw new ServiceException("父分组不存在");
        }
        if (!Objects.equals(parent.getTestProjectId(), testProjectId)) {
            throw new ServiceException("父分组不属于当前项目");
        }
        String parentAncestors = parent.getAncestors();
        if (StrUtil.isBlank(parentAncestors)) {
            return String.valueOf(parentId);
        }
        return parentAncestors + "," + parentId;
    }

    /**
     * 修改分组。
     * 若更换父节点：禁止挂到自身或子孙下；重算本节点祖级，并批量改写子孙祖级前缀。
     * 写入前清空 testProjectId，避免误改所属项目。
     *
     * @param testFlowGroup 修改内容
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestFlowGroup(TestFlowGroup testFlowGroup) {
        if (testFlowGroup.getFlowGroupId() == null) {
            throw new ServiceException("flowGroupId 不能为空");
        }
        TestFlowGroup existing = testFlowGroupMapper.selectTestFlowGroupById(testFlowGroup.getFlowGroupId());
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() != 0)) {
            throw new ServiceException("分组不存在");
        }
        // 禁止经 update 软删或手写祖级；删除走 logicDelete，祖级仅改父时由服务端重算
        testFlowGroup.setDelStatus(null);
        testFlowGroup.setAncestors(null);
        Long projectId = existing.getTestProjectId();
        Long newParentId = testFlowGroup.getParentId();
        boolean parentChanged = newParentId != null && !Objects.equals(newParentId, existing.getParentId());
        if (parentChanged) {
            if (Objects.equals(newParentId, existing.getFlowGroupId())) {
                throw new ServiceException("不能将分组移动到自身下");
            }
            if (newParentId != 0L) {
                List<Long> descendants = selectSelfAndDescendantIds(existing.getFlowGroupId(), projectId);
                if (descendants.contains(newParentId)) {
                    throw new ServiceException("不能将分组移动到其子分组下");
                }
            }
            String newAncestors = resolveAncestors(newParentId, projectId);
            String oldAncestorsPath = StrUtil.blankToDefault(existing.getAncestors(), "0")
                    + "," + existing.getFlowGroupId();
            String newAncestorsPath = newAncestors + "," + existing.getFlowGroupId();
            testFlowGroup.setAncestors(newAncestors);
            // 子孙祖级以旧路径为前缀的，整体替换为新路径
            List<TestFlowGroupResult> all = testFlowGroupMapper.selectTestFlowGroupResultList(
                    TestFlowGroupParams.builder().testProjectId(projectId).build());
            for (TestFlowGroupResult child : all) {
                if (Objects.equals(child.getFlowGroupId(), existing.getFlowGroupId())) {
                    continue;
                }
                String childAncestors = child.getAncestors();
                if (childAncestors != null && (childAncestors.equals(oldAncestorsPath)
                        || childAncestors.startsWith(oldAncestorsPath + ","))) {
                    String updated = newAncestorsPath
                            + childAncestors.substring(oldAncestorsPath.length());
                    TestFlowGroup patch = TestFlowGroup.builder()
                            .flowGroupId(child.getFlowGroupId())
                            .ancestors(updated)
                            .updateTime(DateUtils.getNowDate())
                            .build();
                    testFlowGroupMapper.updateTestFlowGroup(patch);
                }
            }
        }
        testFlowGroup.setUpdateTime(DateUtils.getNowDate());
        testFlowGroup.setTestProjectId(null);
        return testFlowGroupMapper.updateTestFlowGroup(testFlowGroup);
    }

    /**
     * 逻辑删除测试流分组（有子目录则拒绝；组下的流改为未分组）
     *
     * @param flowGroupIdList 待删主键集合
     * @return 实际删除行数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int logicDeleteTestFlowGroupByIdList(List<Long> flowGroupIdList) {
        if (flowGroupIdList == null || flowGroupIdList.isEmpty()) {
            return 0;
        }
        // 先收集待删节点，再按祖级深度降序（子目录优先），避免同批「父在前」误拒
        List<TestFlowGroup> pending = new ArrayList<>();
        for (Long flowGroupId : flowGroupIdList) {
            TestFlowGroup existing = testFlowGroupMapper.selectTestFlowGroupById(flowGroupId);
            if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() != 0)) {
                continue;
            }
            pending.add(existing);
        }
        pending.sort((a, b) -> Integer.compare(ancestorsDepth(b.getAncestors()), ancestorsDepth(a.getAncestors())));
        int rows = 0;
        for (TestFlowGroup existing : pending) {
            Long flowGroupId = existing.getFlowGroupId();
            int childCount = testFlowGroupMapper.countChildrenByParentId(flowGroupId);
            if (childCount > 0) {
                throw new ServiceException("分组「" + existing.getGroupName() + "」下仍有子目录，请先删除子目录");
            }
            // 先软删再清挂流：并发再挂组会因 assertGroupInProject 失败
            rows += testFlowGroupMapper.logicDeleteTestFlowGroupById(flowGroupId);
            testFlowGroupMapper.clearFlowGroupIdOnFlows(flowGroupId);
        }
        return rows;
    }

    /**
     * 祖级串深度：逗号分隔段数（空串视为 0）。
     *
     * @param ancestors 祖级串
     * @return 深度
     */
    private static int ancestorsDepth(String ancestors) {
        if (StrUtil.isBlank(ancestors)) {
            return 0;
        }
        int depth = 1;
        for (int i = 0; i < ancestors.length(); i++) {
            if (ancestors.charAt(i) == ',') {
                depth++;
            }
        }
        return depth;
    }

    /**
     * 查询某分组及全部子孙分组主键。
     *
     * @param flowGroupId   分组主键
     * @param testProjectId 项目主键
     * @return 含自身的主键列表
     */
    @Override
    public List<Long> selectSelfAndDescendantIds(Long flowGroupId, Long testProjectId) {
        return testFlowGroupMapper.selectSelfAndDescendantIds(flowGroupId, testProjectId);
    }

    /**
     * 校验分组存在、未删除且属于指定项目。
     *
     * @param flowGroupId   分组主键，null 则跳过
     * @param testProjectId 项目主键
     */
    @Override
    public void assertGroupInProject(Long flowGroupId, Long testProjectId) {
        if (flowGroupId == null) {
            return;
        }
        TestFlowGroup group = testFlowGroupMapper.selectTestFlowGroupById(flowGroupId);
        if (group == null || (group.getDelStatus() != null && group.getDelStatus() != 0)) {
            throw new ServiceException("分组不存在");
        }
        if (!Objects.equals(group.getTestProjectId(), testProjectId)) {
            throw new ServiceException("分组不属于当前项目");
        }
    }
}
