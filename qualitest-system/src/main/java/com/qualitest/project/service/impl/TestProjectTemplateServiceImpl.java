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
 * 项目模板Service业务层处理
 *
 * @author qualitest
 * @date 2026-09-09
 */
@Service
public class TestProjectTemplateServiceImpl implements ITestProjectTemplateService {

    private final TestProjectTemplateMapper testProjectTemplateMapper;

    public TestProjectTemplateServiceImpl(TestProjectTemplateMapper testProjectTemplateMapper) {
        this.testProjectTemplateMapper = testProjectTemplateMapper;
    }

    /**
     * 查询项目模板列表
     *
     * @param testProjectTemplate 项目模板
     * @return 项目模板
     */
    @Override
    public List<TestProjectTemplate> selectTestProjectTemplateList(TestProjectTemplate testProjectTemplate) {
        return testProjectTemplateMapper.selectTestProjectTemplateList(testProjectTemplate);
    }

    /**
     * 查询项目模板
     *
     * @param testProjectTemplateId 项目模板主键
     * @return 项目模板
     */
    @Override
    public TestProjectTemplate selectTestProjectTemplateById(Long testProjectTemplateId) {
        return testProjectTemplateMapper.selectTestProjectTemplateById(testProjectTemplateId);
    }

    /**
     * 查询项目模板Result列表
     *
     * @param params 项目模板Params
     * @return 项目模板Result集合
     */
    @Override
    public List<TestProjectTemplateResult> selectTestProjectTemplateResultList(TestProjectTemplateParams params) {
        return testProjectTemplateMapper.selectTestProjectTemplateResultList(params);
    }

    /**
     * 获取项目模板详细信息
     *
     * @param testProjectTemplateId 项目模板主键
     * @return 项目模板Result
     */
    @Override
    public TestProjectTemplateResult selectTestProjectTemplateResult(Long testProjectTemplateId) {
        return testProjectTemplateMapper.selectTestProjectTemplateResult(testProjectTemplateId);
    }

    /**
     * 新增项目模板（管理端 CRUD：预制测试流强制为空）
     *
     * @param testProjectTemplate 项目模板
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProjectTemplate(TestProjectTemplate testProjectTemplate) {
        return doInsert(testProjectTemplate, false);
    }

    /**
     * 新增项目模板（完整包导入 / 另存：允许写入预制测试流）
     *
     * @param testProjectTemplate 项目模板
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTemplatePack(TestProjectTemplate testProjectTemplate) {
        return doInsert(testProjectTemplate, true);
    }

    /**
     * 修改项目模板（管理端 CRUD：保留库内预制测试流）
     *
     * @param testProjectTemplate 项目模板
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProjectTemplate(TestProjectTemplate testProjectTemplate) {
        return doUpdate(testProjectTemplate, false);
    }

    /**
     * 修改项目模板（完整包同名覆盖：允许改写预制测试流）
     *
     * @param testProjectTemplate 项目模板
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTemplatePack(TestProjectTemplate testProjectTemplate) {
        return doUpdate(testProjectTemplate, true);
    }

    /**
     * 新增落库
     *
     * @param entity     项目模板
     * @param allowFlows true 保留实体上的预制流；false 强制 templateFlows=[]
     * @return 结果
     */
    private int doInsert(TestProjectTemplate entity, boolean allowFlows) {
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
        if (!allowFlows) {
            entity.setTemplateFlows("[]");
        }
        validateWritable(entity, null);
        if (Objects.isNull(entity.getTestProjectTemplateId())) {
            entity.setTestProjectTemplateId(IdUtil.getSnowflakeNextId());
        }
        entity.setCreateTime(DateUtils.getNowDate());
        return testProjectTemplateMapper.insertTestProjectTemplate(entity);
    }

    /**
     * 修改落库
     *
     * @param entity     项目模板
     * @param allowFlows true 用请求体预制流覆盖；false 写回库内原 templateFlows
     * @return 结果
     */
    private int doUpdate(TestProjectTemplate entity, boolean allowFlows) {
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
        if (!allowFlows) {
            entity.setTemplateFlows(existing.getTemplateFlows());
        }
        validateWritable(entity, existing.getTestProjectTemplateId());
        entity.setUpdateTime(DateUtils.getNowDate());
        return testProjectTemplateMapper.updateTestProjectTemplate(entity);
    }

    /**
     * 批量删除项目模板
     *
     * @param testProjectTemplateIdList 需要删除的项目模板主键集合
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteTestProjectTemplateByIdList(List<Long> testProjectTemplateIdList) {
        if (testProjectTemplateIdList == null || testProjectTemplateIdList.isEmpty()) {
            return 0;
        }
        assertCustomDeletable(testProjectTemplateIdList);
        return testProjectTemplateMapper.deleteTestProjectTemplateByIdList(testProjectTemplateIdList);
    }

    /**
     * 删除项目模板信息
     *
     * @param testProjectTemplateId 项目模板主键
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteTestProjectTemplateById(Long testProjectTemplateId) {
        if (testProjectTemplateId == null) {
            return 0;
        }
        assertCustomDeletable(testProjectTemplateId);
        return testProjectTemplateMapper.deleteTestProjectTemplateById(testProjectTemplateId);
    }

    /**
     * 逻辑删除项目模板信息
     *
     * @param testProjectTemplateId 项目模板主键
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int logicDeleteTestProjectTemplateById(Long testProjectTemplateId) {
        if (testProjectTemplateId == null) {
            return 0;
        }
        assertCustomDeletable(testProjectTemplateId);
        return testProjectTemplateMapper.logicDeleteTestProjectTemplateById(testProjectTemplateId);
    }

    /**
     * 批量逻辑删除项目模板信息
     *
     * @param testProjectTemplateIdList 项目模板主键集合
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int logicDeleteTestProjectTemplateByIdList(List<Long> testProjectTemplateIdList) {
        if (testProjectTemplateIdList == null || testProjectTemplateIdList.isEmpty()) {
            return 0;
        }
        assertCustomDeletable(testProjectTemplateIdList);
        return testProjectTemplateMapper.logicDeleteTestProjectTemplateByIdList(testProjectTemplateIdList);
    }

    /**
     * 查询项目模板数量
     *
     * @param params 项目模板Params
     * @return 数量
     */
    @Override
    public int selectTestProjectTemplateCount(TestProjectTemplateParams params) {
        return testProjectTemplateMapper.selectTestProjectTemplateCount(params);
    }

    /**
     * 按条件查询单条项目模板
     *
     * @param params 项目模板Params
     * @return 项目模板
     */
    @Override
    public TestProjectTemplate selectTestProjectTemplateOne(TestProjectTemplateParams params) {
        return testProjectTemplateMapper.selectTestProjectTemplateOne(params);
    }

    /**
     * 查询已启用且未删除的项目模板列表
     *
     * @return 项目模板Result集合
     */
    @Override
    public List<TestProjectTemplateResult> selectEnabledList() {
        return testProjectTemplateMapper.selectEnabledTestProjectTemplateList();
    }

    /**
     * 克隆为自定义项目模板
     *
     * @param testProjectTemplateId 源模板主键
     * @return 新模板主键
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
                .templateEnvs(source.getTemplateEnvs())
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

    /**
     * 按主键取未删除模板
     *
     * @param id 项目模板主键
     * @return 项目模板
     */
    private TestProjectTemplate requireExisting(Long id) {
        TestProjectTemplate existing = testProjectTemplateMapper.selectTestProjectTemplateById(id);
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() == 1)) {
            throw new ServiceException("模板不存在");
        }
        return existing;
    }

    /**
     * 断言可删除（内置不可删）
     *
     * @param id 项目模板主键
     */
    private void assertCustomDeletable(Long id) {
        if (isBuiltin(requireExisting(id))) {
            throw new ServiceException("内置模板不可删除");
        }
    }

    /**
     * 断言集合均可删除
     *
     * @param idList 项目模板主键集合
     */
    private void assertCustomDeletable(List<Long> idList) {
        for (Long id : idList) {
            assertCustomDeletable(id);
        }
    }

    /**
     * 写入前校验：名称唯一、预制接口非空；空 JSON 列补 []
     *
     * @param entity    项目模板
     * @param excludeId 改名时排除自身主键，新增传 null
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
        if (StrUtil.isBlank(entity.getTemplateEnvs())) {
            entity.setTemplateEnvs("[]");
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

    /**
     * 生成克隆名称（原名 +「 (副本)」，冲突则加序号）
     *
     * @param sourceName 源模板名称
     * @return 可用名称
     */
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

    /**
     * 是否内置模板
     *
     * @param row 项目模板
     * @return true 内置
     */
    private boolean isBuiltin(TestProjectTemplate row) {
        return row != null && row.getBuiltinStatus() != null && row.getBuiltinStatus() == 1;
    }
}
