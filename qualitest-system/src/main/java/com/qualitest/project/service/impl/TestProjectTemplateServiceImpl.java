package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.mapper.TestProjectTemplateMapper;
import com.qualitest.project.params.TestProjectTemplateParams;
import com.qualitest.project.result.TestProjectTemplateResult;
import com.qualitest.project.service.ITestProjectTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 项目模板业务。
 * 内置模板只读可克隆；自定义模板可改可删；名称在未删除范围内唯一。
 * 保存时预制接口必填；预制参数 / 预制测试流缺省写成 []。
 */
@Service
public class TestProjectTemplateServiceImpl implements ITestProjectTemplateService {

    private final TestProjectTemplateMapper testProjectTemplateMapper;

    public TestProjectTemplateServiceImpl(TestProjectTemplateMapper testProjectTemplateMapper) {
        this.testProjectTemplateMapper = testProjectTemplateMapper;
    }

    @Override
    public List<TestProjectTemplate> selectTestProjectTemplateList(TestProjectTemplate entity) {
        return testProjectTemplateMapper.selectTestProjectTemplateList(entity);
    }

    @Override
    public TestProjectTemplate selectTestProjectTemplateById(Long testProjectTemplateId) {
        return testProjectTemplateMapper.selectTestProjectTemplateById(testProjectTemplateId);
    }

    @Override
    public List<TestProjectTemplateResult> selectTestProjectTemplateResultList(TestProjectTemplateParams params) {
        return testProjectTemplateMapper.selectTestProjectTemplateResultList(params);
    }

    @Override
    public TestProjectTemplateResult selectTestProjectTemplateResult(Long testProjectTemplateId) {
        return testProjectTemplateMapper.selectTestProjectTemplateResult(testProjectTemplateId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProjectTemplate(TestProjectTemplate entity) {
        if (entity == null) {
            throw new ServiceException("模板不能为空");
        }
        entity.setBuiltinStatus(0);
        if (entity.getEnableStatus() == null) {
            entity.setEnableStatus(1);
        }
        if (entity.getSortNum() == null) {
            entity.setSortNum(0);
        }
        if (entity.getDelStatus() == null) {
            entity.setDelStatus(0);
        }
        validateWritable(entity, null);
        if (Objects.isNull(entity.getTestProjectTemplateId())) {
            entity.setTestProjectTemplateId(IdUtil.getSnowflakeNextId());
        }
        entity.setCreateTime(DateUtils.getNowDate());
        return testProjectTemplateMapper.insertTestProjectTemplate(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProjectTemplate(TestProjectTemplate entity) {
        if (entity == null || entity.getTestProjectTemplateId() == null) {
            throw new ServiceException("模板不存在");
        }
        TestProjectTemplate existing = requireExisting(entity.getTestProjectTemplateId());
        if (isBuiltin(existing)) {
            throw new ServiceException("内置模板只读，请克隆后修改");
        }
        if (entity.getBuiltinStatus() != null && entity.getBuiltinStatus() == 1) {
            throw new ServiceException("不可将自定义模板标记为内置");
        }
        validateWritable(entity, existing.getTestProjectTemplateId());
        entity.setUpdateTime(DateUtils.getNowDate());
        return testProjectTemplateMapper.updateTestProjectTemplate(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteTestProjectTemplateByIdList(List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            return 0;
        }
        assertCustomDeletable(idList);
        return testProjectTemplateMapper.deleteTestProjectTemplateByIdList(idList);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteTestProjectTemplateById(Long testProjectTemplateId) {
        if (testProjectTemplateId == null) {
            return 0;
        }
        assertCustomDeletable(testProjectTemplateId);
        return testProjectTemplateMapper.deleteTestProjectTemplateById(testProjectTemplateId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int logicDeleteTestProjectTemplateById(Long testProjectTemplateId) {
        if (testProjectTemplateId == null) {
            return 0;
        }
        assertCustomDeletable(testProjectTemplateId);
        return testProjectTemplateMapper.logicDeleteTestProjectTemplateById(testProjectTemplateId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int logicDeleteTestProjectTemplateByIdList(List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            return 0;
        }
        assertCustomDeletable(idList);
        return testProjectTemplateMapper.logicDeleteTestProjectTemplateByIdList(idList);
    }

    @Override
    public int selectTestProjectTemplateCount(TestProjectTemplateParams params) {
        return testProjectTemplateMapper.selectTestProjectTemplateCount(params);
    }

    @Override
    public TestProjectTemplate selectTestProjectTemplateOne(TestProjectTemplateParams params) {
        return testProjectTemplateMapper.selectTestProjectTemplateOne(params);
    }

    @Override
    public List<TestProjectTemplateResult> selectEnabledList() {
        return testProjectTemplateMapper.selectEnabledTestProjectTemplateList();
    }

    /**
     * 克隆模板为自定义副本。
     * 复制路径匹配、预制接口、预制参数、预制测试流、预制提示词；名称加「 (副本)」后缀并去重。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long cloneTestProjectTemplate(Long testProjectTemplateId) {
        TestProjectTemplate source = requireExisting(testProjectTemplateId);
        TestProjectTemplate copy = TestProjectTemplate.builder()
                .testProjectTemplateId(IdUtil.getSnowflakeNextId())
                .templateName(uniqueCloneName(source.getTemplateName()))
                .matchConfig(source.getMatchConfig())
                .templateApis(source.getTemplateApis())
                .templateParams(source.getTemplateParams())
                .templateFlows(source.getTemplateFlows())
                .templatePrompts(source.getTemplatePrompts())
                .builtinStatus(0)
                .enableStatus(1)
                .sortNum(source.getSortNum() != null ? source.getSortNum() : 0)
                .delStatus(0)
                .build();
        copy.setRemark(source.getRemark());
        copy.setCreateTime(DateUtils.getNowDate());
        testProjectTemplateMapper.insertTestProjectTemplate(copy);
        return copy.getTestProjectTemplateId();
    }

    /** 未删除才返回，否则抛「模板不存在」。 */
    private TestProjectTemplate requireExisting(Long id) {
        TestProjectTemplate existing = testProjectTemplateMapper.selectTestProjectTemplateById(id);
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() == 1)) {
            throw new ServiceException("模板不存在");
        }
        return existing;
    }

    /** 存在且非内置才允许删除。 */
    private void assertCustomDeletable(Long id) {
        if (isBuiltin(requireExisting(id))) {
            throw new ServiceException("内置模板不可删除");
        }
    }

    private void assertCustomDeletable(List<Long> idList) {
        for (Long id : idList) {
            assertCustomDeletable(id);
        }
    }

    /**
     * 写入前校验：名称非空且未删范围内唯一；预制接口非空；
     * 预制参数 / 预制测试流 / 预制提示词为空时写成 []。不校验托管头（勾选进项目时再生成）。
     */
    private void validateWritable(TestProjectTemplate entity, Long excludeId) {
        if (StrUtil.isBlank(entity.getTemplateName())) {
            throw new ServiceException("模板名称不能为空");
        }
        if (StrUtil.isBlank(entity.getTemplateApis()) || "[]".equals(entity.getTemplateApis().trim())) {
            throw new ServiceException("预制接口不能为空");
        }
        if (StrUtil.isBlank(entity.getTemplateParams())) {
            entity.setTemplateParams("[]");
        }
        if (StrUtil.isBlank(entity.getTemplateFlows())) {
            entity.setTemplateFlows("[]");
        }
        if (StrUtil.isBlank(entity.getTemplatePrompts())) {
            entity.setTemplatePrompts("[]");
        }
        String name = entity.getTemplateName().trim();
        entity.setTemplateName(name);
        if (testProjectTemplateMapper.countByTemplateName(name, excludeId) > 0) {
            throw new ServiceException("模板名称已存在: " + name);
        }
    }

    /** 生成克隆名称：原名 +「 (副本)」，重名则再加序号。 */
    private String uniqueCloneName(String sourceName) {
        String base = StrUtil.blankToDefault(sourceName, "模板") + " (副本)";
        if (testProjectTemplateMapper.countByTemplateName(base, null) == 0) {
            return base;
        }
        for (int i = 2; i < 100; i++) {
            String candidate = base + i;
            if (testProjectTemplateMapper.countByTemplateName(candidate, null) == 0) {
                return candidate;
            }
        }
        return base + IdUtil.getSnowflakeNextIdStr();
    }

    /** 是否内置模板。 */
    private boolean isBuiltin(TestProjectTemplate row) {
        return row != null && row.getBuiltinStatus() != null && row.getBuiltinStatus() == 1;
    }
}
