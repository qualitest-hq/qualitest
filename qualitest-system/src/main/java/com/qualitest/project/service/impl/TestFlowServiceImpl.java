package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.subflow.SubflowTemplateCatalog;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationOptions;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.mapper.TestFlowMapper;
import com.qualitest.project.params.CreateSubflowFromTemplateParams;
import com.qualitest.project.params.TestFlowParams;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return testFlowMapper.selectTestFlowResultList(params);
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
        validateGraphJsonForPersist(testFlow.getGraphJson());
        testFlow.setCreateTime(DateUtils.getNowDate());
        return testFlowMapper.insertTestFlow(testFlow);
    }

    /**
     * 修改测试流
     *
     * @param testFlow 测试流
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestFlow(TestFlow testFlow) {
        validateGraphJsonForPersist(testFlow.getGraphJson());
        testFlow.setUpdateTime(DateUtils.getNowDate());
        return testFlowMapper.updateTestFlow(testFlow);
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
