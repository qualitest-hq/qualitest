package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.subflow.SubflowTemplateCatalog;
import com.qualitest.common.utils.ServletUtils;
import com.qualitest.flow.sync.FlowEditLeaseService;
import com.qualitest.flow.sync.FlowExternalChangePublisher;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.flow.sync.FlowGraphCommitPatchHolder;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationOptions;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.mapper.TestFlowMapper;
import com.qualitest.project.params.CreateSubflowFromTemplateParams;
import com.qualitest.project.params.TestFlowParams;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowGroupService;
import com.qualitest.project.service.ITestFlowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;

/**
 * 测试流Service业务层处理
 *
 * @author qualitest
 * @date 2026-06-05
 */
@Service
public class TestFlowServiceImpl implements ITestFlowService {
    @Autowired
    private TestFlowMapper testFlowMapper;

    @Autowired
    private GraphJsonValidator graphJsonValidator;

    @Autowired
    private FlowExternalChangePublisher flowExternalChangePublisher;

    @Autowired
    private FlowEditLeaseService flowEditLeaseService;

    @Autowired
    private ITestFlowGroupService testFlowGroupService;

    /**
     * 查询测试流列表
     *
     * @param testFlow 测试流
     * @return 测试流
     */
    @Override
    public List<TestFlow> selectTestFlowList(TestFlow testFlow) {
        return testFlowMapper.selectTestFlowList(testFlow);
    }

    /**
     * 查询测试流
     *
     * @param testFlowId 测试流主键
     * @return 测试流
     */
    @Override
    public TestFlow selectTestFlowById(Long testFlowId) {
        return testFlowMapper.selectTestFlowById(testFlowId);
    }

    /**
     * 查询测试流Result列表
     *
     * @param params 测试流Params
     * @return 测试流Result集合
     */
    @Override
    public List<TestFlowResult> selectTestFlowResultList(TestFlowParams params) {
        prepareFlowGroupFilter(params);
        return testFlowMapper.selectTestFlowResultList(params);
    }

    /**
     * 整理列表目录过滤条件。
     * 未分组：只保留 ungroupedOnly；选中目录：展开为含子孙的 flowGroupIdList；
     * 无项目 id 时无法展开子孙，至少按当前目录主键过滤，避免漏掉条件查出全量。
     *
     * @param params 列表查询参数
     */
    private void prepareFlowGroupFilter(TestFlowParams params) {
        if (params == null) {
            return;
        }
        if (Boolean.TRUE.equals(params.getUngroupedOnly())) {
            params.setFlowGroupIdList(null);
            return;
        }
        if (params.getFlowGroupId() == null) {
            return;
        }
        Long projectId = params.getTestProjectId();
        if (projectId == null) {
            // 无法展开子孙时至少按本节点过滤，避免静默变成全量
            params.setFlowGroupIdList(List.of(params.getFlowGroupId()));
            return;
        }
        List<Long> ids = testFlowGroupService.selectSelfAndDescendantIds(params.getFlowGroupId(), projectId);
        if (ids == null || ids.isEmpty()) {
            params.setFlowGroupIdList(List.of(params.getFlowGroupId()));
            return;
        }
        params.setFlowGroupIdList(ids);
    }

    /**
     * 新建或改挂目录时：确认目录存在、未删除且与测试流同属一项目。
     *
     * @param testFlow 测试流（须带 flowGroupId 才校验）
     */
    private void validateFlowGroupAssignment(TestFlow testFlow) {
        if (testFlow == null || testFlow.getFlowGroupId() == null) {
            return;
        }
        Long projectId = testFlow.getTestProjectId();
        if (projectId == null && testFlow.getTestFlowId() != null) {
            TestFlow existing = testFlowMapper.selectTestFlowById(testFlow.getTestFlowId());
            if (existing != null) {
                projectId = existing.getTestProjectId();
            }
        }
        testFlowGroupService.assertGroupInProject(testFlow.getFlowGroupId(), projectId);
    }

    /**
     * 获取测试流详细信息
     *
     * @param testFlowId 测试流主键
     * @return 测试流Result
     */
    @Override
    public TestFlowResult selectTestFlowResult(Long testFlowId) {
        return testFlowMapper.selectTestFlowResult(testFlowId);
    }

    /**
     * 新增测试流
     *
     * @param testFlow 测试流
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestFlow(TestFlow testFlow) {
        if (Objects.isNull(testFlow.getTestFlowId())) {
            testFlow.setTestFlowId(IdUtil.getSnowflakeNextId());
        }
        validateFlowGroupAssignment(testFlow);
        validateGraphJsonForPersist(testFlow.getGraphJson());
        testFlow.setCreateTime(DateUtils.getNowDate());
        int rows = testFlowMapper.insertTestFlow(testFlow);
        if (rows > 0 && testFlow.getTestFlowId() != null) {
            flowExternalChangePublisher.publishFlowCreated(
                    testFlow.getTestFlowId(),
                    testFlow.getTestProjectId(),
                    FlowExternalChangeSourceHolder.getOrDefault());
        }
        return rows;
    }

    /**
     * 修改测试流。
     * 写 graph_json 时先占写锁（请求头带有效租约则续期不换锁，否则短抢短释）；
     * 成功后发图提交或元数据变更通知；短抢锁在 finally 释放。
     *
     * @param testFlow 测试流（可只改名称，或带 graphJson 改图）
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestFlow(TestFlow testFlow) {
        boolean clearGroup = Boolean.TRUE.equals(testFlow.getClearFlowGroup());
        testFlow.setClearFlowGroup(null);
        if (clearGroup) {
            // 同请求若又带了 flowGroupId，以清空为准
            testFlow.setFlowGroupId(null);
        }
        validateFlowGroupAssignment(testFlow);
        validateGraphJsonForPersist(testFlow.getGraphJson());
        boolean writingGraph = StrUtil.isNotBlank(testFlow.getGraphJson());
        FlowEditLeaseService.LeaseHandle lease = null;
        try {
            if (writingGraph && testFlow.getTestFlowId() != null) {
                lease = flowEditLeaseService.beginWrite(
                        testFlow.getTestFlowId(),
                        FlowExternalChangeSourceHolder.getOrDefault(),
                        readClientLeaseToken());
            }
            testFlow.setUpdateTime(DateUtils.getNowDate());
            int rows = testFlowMapper.updateTestFlow(testFlow);
            if (clearGroup && testFlow.getTestFlowId() != null) {
                int cleared = testFlowMapper.clearFlowGroupId(testFlow.getTestFlowId());
                if (cleared > 0) {
                    rows = Math.max(rows, cleared);
                }
            }
            if (rows > 0 && testFlow.getTestFlowId() != null) {
                Long projectId = testFlow.getTestProjectId();
                if (projectId == null) {
                    TestFlow existing = testFlowMapper.selectTestFlowById(testFlow.getTestFlowId());
                    if (existing != null) {
                        projectId = existing.getTestProjectId();
                    }
                }
                String source = FlowExternalChangeSourceHolder.getOrDefault();
                if (writingGraph) {
                    // 全自动落盘可能已放入本批节点/边片段；人手保存一般为 null
                    FlowGraphCommitPatchHolder.PatchPayload patch = FlowGraphCommitPatchHolder.get();
                    flowExternalChangePublisher.publishGraphCommitted(
                            testFlow.getTestFlowId(),
                            projectId,
                            source,
                            testFlow.getUpdateTime(),
                            patch);
                } else {
                    flowExternalChangePublisher.publishFlowMetaChanged(
                            testFlow.getTestFlowId(), projectId, source);
                }
            }
            return rows;
        } finally {
            // 客户端长持锁不在此释放；短抢锁写完即放
            if (lease != null && !lease.heldFromClient() && testFlow.getTestFlowId() != null) {
                flowEditLeaseService.release(testFlow.getTestFlowId(), lease.token());
            }
            FlowGraphCommitPatchHolder.clear();
        }
    }

    /**
     * 清空测试流所属目录（变为未分组），并发元数据变更通知。
     *
     * @param testFlowId 测试流主键
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int clearFlowGroupId(Long testFlowId) {
        if (testFlowId == null) {
            return 0;
        }
        int rows = testFlowMapper.clearFlowGroupId(testFlowId);
        if (rows > 0) {
            TestFlow existing = testFlowMapper.selectTestFlowById(testFlowId);
            Long projectId = existing != null ? existing.getTestProjectId() : null;
            flowExternalChangePublisher.publishFlowMetaChanged(
                    testFlowId, projectId, FlowExternalChangeSourceHolder.getOrDefault());
        }
        return rows;
    }

    /**
     * 从当前 HTTP 请求读取写锁租约头。
     * 无 Web 请求上下文（例如 MCP 调用线程）返回 null，走短抢短释。
     */
    private static String readClientLeaseToken() {
        try {
            ServletRequestAttributes attrs = ServletUtils.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            if (request == null) {
                return null;
            }
            String token = request.getHeader(FlowEditLeaseService.HEADER_NAME);
            return token != null && !token.isBlank() ? token.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 写库前校验 graph_json。
     * 仅拒绝：无法解析、节点缺 id / id 重复、边缺 source/target。
     * 不拒绝：开始节点异常、断言路径错误、鉴权缺失、HTTP 缺必填等（允许半成品落盘）。
     * graphJson 为空时跳过（只改名称等元数据时可不带图）。
     */
    private void validateGraphJsonForPersist(String graphJson) {
        if (StrUtil.isBlank(graphJson)) {
            return;
        }
        GraphJson graph;
        try {
            graph = GraphJson.parse(graphJson);
        } catch (Exception e) {
            throw new ServiceException("graph_json 无法解析：" + e.getMessage());
        }
        if (graph == null) {
            throw new ServiceException("graph_json 无法解析：解析结果为空");
        }
        GraphValidationResult validation =
                graphJsonValidator.validate(graph, GraphValidationOptions.persistMinimal());
        if (!validation.isOk() && !validation.getErrors().isEmpty()) {
            throw new ServiceException(validation.getErrors().get(0));
        }
    }

    /**
     * 批量删除测试流
     *
     * @param testFlowIdList 需要删除的测试流主键集合
     * @return 结果
     */
    @Override
    public int deleteTestFlowByIdList(List<Long> testFlowIdList) {
        return testFlowMapper.deleteTestFlowByIdList(testFlowIdList);
    }

    /**
     * 删除测试流信息
     *
     * @param testFlowId 测试流主键
     * @return 结果
     */
    @Override
    public int deleteTestFlowById(Long testFlowId) {
        return testFlowMapper.deleteTestFlowById(testFlowId);
    }

    /**
     * 逻辑删除测试流信息
     *
     * @param testFlowId 测试流主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestFlowById(Long testFlowId) {
        return testFlowMapper.logicDeleteTestFlowById(testFlowId);
    }

    /**
     * 批量逻辑删除测试流信息
     *
     * @param testFlowIdList 测试流主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestFlowByIdList(List<Long> testFlowIdList) {
        return testFlowMapper.logicDeleteTestFlowByIdList(testFlowIdList);
    }

    /**
     * 查询测试流数量
     *
     * @param params 测试流Params
     * @return 数量
     */
    @Override
    public int selectTestFlowCount(TestFlowParams params) {
        return testFlowMapper.selectTestFlowCount(params);
    }

    /**
     * 按条件查询单条测试流
     *
     * @param params 测试流Params
     * @return 测试流
     */
    @Override
    public TestFlow selectTestFlowOne(TestFlowParams params) {
        return testFlowMapper.selectTestFlowOne(params);
    }

    /**
     * 从平台子流模板创建项目内测试流记录。
     * 写入模板图骨架、契约 JSON 与说明，返回新 test_flow_id。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromSubflowTemplate(CreateSubflowFromTemplateParams params) {
        if (params == null || params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        if (StrUtil.isBlank(params.getTemplateId())) {
            throw new ServiceException("templateId 不能为空");
        }
        JSONObject template = SubflowTemplateCatalog.findById(params.getTemplateId().trim());
        if (template == null) {
            throw new ServiceException("子流模板不存在: " + params.getTemplateId());
        }
        String graphJson = SubflowTemplateCatalog.loadGraphJson(params.getTemplateId().trim());
        if (graphJson == null || graphJson.isBlank()) {
            throw new ServiceException("子流模板图不可用: " + params.getTemplateId());
        }
        TestFlow flow = new TestFlow();
        flow.setTestProjectId(params.getTestProjectId());
        flow.setFlowName(StrUtil.blankToDefault(params.getFlowName(), template.getString("name")));
        flow.setFlowDescription(template.getString("description"));
        flow.setGraphJson(SubflowTemplateCatalog.embedFlowOutputsIntoGraph(graphJson, template));
        flow.setDelStatus(0);
        insertTestFlow(flow);
        return flow.getTestFlowId();
    }
}
