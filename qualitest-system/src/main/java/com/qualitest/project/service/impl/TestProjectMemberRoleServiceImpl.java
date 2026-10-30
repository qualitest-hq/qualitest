package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.core.redis.RedisCache;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestProjectMemberRole;
import com.qualitest.project.domain.TestProjectMemberRolePerm;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.mapper.TestProjectMemberMapper;
import com.qualitest.project.mapper.TestProjectMemberRoleMapper;
import com.qualitest.project.mapper.TestProjectMemberRolePermMapper;
import com.qualitest.project.params.TestProjectMemberRoleParams;
import com.qualitest.project.params.TestProjectMemberRoleSaveParams;
import com.qualitest.project.result.TestProjectMemberRoleResult;
import com.qualitest.project.service.ITestProjectMemberRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 测试项目成员角色Service业务层处理
 *
 * @author qualitest
 * @date 2026-10-29
 */
@Service
public class TestProjectMemberRoleServiceImpl implements ITestProjectMemberRoleService {

    /** Redis 键前缀：角色 role_key → 权限 code 集合 */
    private static final String CACHE_KEY_PREFIX = "project:memberRole:perms:";

    /** 角色权限缓存过期小时数 */
    private static final int CACHE_HOURS = 24;

    /** 角色主表 */
    @Autowired
    private TestProjectMemberRoleMapper testProjectMemberRoleMapper;

    /** 角色权限关联表 */
    @Autowired
    private TestProjectMemberRolePermMapper testProjectMemberRolePermMapper;

    /** 项目成员表 */
    @Autowired
    private TestProjectMemberMapper testProjectMemberMapper;

    /** 角色权限缓存 */
    @Autowired
    private RedisCache redisCache;

    /**
     * 查询测试项目成员角色列表
     *
     * @param testProjectMemberRole 测试项目成员角色
     * @return 测试项目成员角色
     */
    @Override
    public List<TestProjectMemberRole> selectTestProjectMemberRoleList(TestProjectMemberRole testProjectMemberRole) {
        return testProjectMemberRoleMapper.selectTestProjectMemberRoleList(testProjectMemberRole);
    }

    /**
     * 查询测试项目成员角色
     *
     * @param testProjectMemberRoleId 测试项目成员角色主键
     * @return 测试项目成员角色
     */
    @Override
    public TestProjectMemberRole selectTestProjectMemberRoleById(Long testProjectMemberRoleId) {
        return testProjectMemberRoleMapper.selectTestProjectMemberRoleById(testProjectMemberRoleId);
    }

    /**
     * 查询测试项目成员角色Result列表
     *
     * @param params 测试项目成员角色Params
     * @return 测试项目成员角色Result集合
     */
    @Override
    public List<TestProjectMemberRoleResult> selectTestProjectMemberRoleResultList(TestProjectMemberRoleParams params) {
        return testProjectMemberRoleMapper.selectTestProjectMemberRoleResultList(params);
    }

    /**
     * 获取测试项目成员角色详细信息
     *
     * @param testProjectMemberRoleId 测试项目成员角色主键
     * @return 测试项目成员角色Result
     */
    @Override
    public TestProjectMemberRoleResult selectTestProjectMemberRoleResult(Long testProjectMemberRoleId) {
        TestProjectMemberRoleResult result =
                testProjectMemberRoleMapper.selectTestProjectMemberRoleResult(testProjectMemberRoleId);
        if (result != null) {
            result.setPermCodes(testProjectMemberRolePermMapper.selectPermCodesByRoleId(testProjectMemberRoleId));
        }
        return result;
    }

    /**
     * 新增测试项目成员角色
     *
     * @param testProjectMemberRole 测试项目成员角色
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProjectMemberRole(TestProjectMemberRole testProjectMemberRole) {
        if (Objects.isNull(testProjectMemberRole.getTestProjectMemberRoleId())) {
            testProjectMemberRole.setTestProjectMemberRoleId(IdUtil.getSnowflakeNextId());
        }
        if (testProjectMemberRole.getDelStatus() == null) {
            testProjectMemberRole.setDelStatus(0L);
        }
        if (testProjectMemberRole.getEnableStatus() == null) {
            testProjectMemberRole.setEnableStatus(1L);
        }
        testProjectMemberRole.setCreateTime(DateUtils.getNowDate());
        return testProjectMemberRoleMapper.insertTestProjectMemberRole(testProjectMemberRole);
    }

    /**
     * 修改测试项目成员角色
     *
     * @param testProjectMemberRole 测试项目成员角色
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProjectMemberRole(TestProjectMemberRole testProjectMemberRole) {
        testProjectMemberRole.setUpdateTime(DateUtils.getNowDate());
        return testProjectMemberRoleMapper.updateTestProjectMemberRole(testProjectMemberRole);
    }

    /**
     * 批量删除测试项目成员角色
     *
     * @param testProjectMemberRoleIdList 需要删除的测试项目成员角色主键集合
     * @return 结果
     */
    @Override
    public int deleteTestProjectMemberRoleByIdList(List<Long> testProjectMemberRoleIdList) {
        return testProjectMemberRoleMapper.deleteTestProjectMemberRoleByIdList(testProjectMemberRoleIdList);
    }

    /**
     * 删除测试项目成员角色信息
     *
     * @param testProjectMemberRoleId 测试项目成员角色主键
     * @return 结果
     */
    @Override
    public int deleteTestProjectMemberRoleById(Long testProjectMemberRoleId) {
        return testProjectMemberRoleMapper.deleteTestProjectMemberRoleById(testProjectMemberRoleId);
    }

    /**
     * 修改测试项目成员角色为逻辑删除
     *
     * @param testProjectMemberRoleId 测试项目成员角色ID
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectMemberRoleById(Long testProjectMemberRoleId) {
        assertRoleDeletable(testProjectMemberRoleId);
        TestProjectMemberRole existing = testProjectMemberRoleMapper.selectTestProjectMemberRoleById(testProjectMemberRoleId);
        if (existing == null) {
            return 0;
        }
        // 软删前改写 role_key，避免 UNIQUE(role_key) 阻止重建同名角色
        String deletedKey = existing.getRoleKey() + "__deleted__" + testProjectMemberRoleId;
        TestProjectMemberRole keyRewrite = TestProjectMemberRole.builder()
                .testProjectMemberRoleId(testProjectMemberRoleId)
                .roleKey(deletedKey)
                .build();
        keyRewrite.setUpdateTime(DateUtils.getNowDate());
        testProjectMemberRoleMapper.updateTestProjectMemberRole(keyRewrite);
        int rows = testProjectMemberRoleMapper.logicDeleteTestProjectMemberRoleById(testProjectMemberRoleId);
        evictPermCache(existing.getRoleKey());
        evictPermCache(deletedKey);
        testProjectMemberRolePermMapper.deleteTestProjectMemberRolePermByRoleId(testProjectMemberRoleId);
        return rows;
    }

    /**
     * 批量修改测试项目成员角色为逻辑删除
     *
     * @param testProjectMemberRoleIdList 测试项目成员角色ID集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectMemberRoleByIdList(List<Long> testProjectMemberRoleIdList) {
        int total = 0;
        if (testProjectMemberRoleIdList == null) {
            return 0;
        }
        for (Long id : testProjectMemberRoleIdList) {
            total += logicDeleteTestProjectMemberRoleById(id);
        }
        return total;
    }

    /**
     * 查询测试项目成员角色数量
     *
     * @param params 测试项目成员角色Params
     * @return 数量
     */
    @Override
    public int selectTestProjectMemberRoleCount(TestProjectMemberRoleParams params) {
        return testProjectMemberRoleMapper.selectTestProjectMemberRoleCount(params);
    }

    /**
     * 按条件查询单条测试项目成员角色
     *
     * @param params 测试项目成员角色Params
     * @return 测试项目成员角色
     */
    @Override
    public TestProjectMemberRole selectTestProjectMemberRoleOne(TestProjectMemberRoleParams params) {
        return testProjectMemberRoleMapper.selectTestProjectMemberRoleOne(params);
    }

    /**
     * 按角色标识查询启用中的权限集合（含缓存）
     *
     * @param roleKey 角色标识
     * @return 权限 code；角色不存在或已禁用时为空集
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> listPermCodesByRoleKey(String roleKey) {
        String normalized = com.qualitest.project.enums.TestProjectMemberRole.normalizeCode(roleKey);
        if (StrUtil.isBlank(normalized)) {
            return Collections.emptySet();
        }
        if (com.qualitest.project.enums.TestProjectMemberRole.isSysAdmin(normalized)) {
            return new LinkedHashSet<>(TestProjectMemberPerm.allCodes());
        }
        String cacheKey = CACHE_KEY_PREFIX + normalized;
        Object cached = redisCache.getCacheObject(cacheKey);
        if (cached instanceof Set) {
            return new LinkedHashSet<>((Set<String>) cached);
        }
        TestProjectMemberRole role = testProjectMemberRoleMapper.selectTestProjectMemberRoleOne(
                TestProjectMemberRoleParams.builder()
                        .roleKey(normalized)
                        .enableStatus(1L)
                        .delStatus(0L)
                        .build());
        if (role == null) {
            redisCache.setCacheObject(cacheKey, Collections.emptySet(), CACHE_HOURS, TimeUnit.HOURS);
            return Collections.emptySet();
        }
        List<String> codes = testProjectMemberRolePermMapper.selectPermCodesByRoleId(role.getTestProjectMemberRoleId());
        Set<String> set = codes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(codes);
        redisCache.setCacheObject(cacheKey, set, CACHE_HOURS, TimeUnit.HOURS);
        return set;
    }

    /**
     * 失效全部角色权限缓存
     */
    @Override
    public void clearPermCache() {
        // 清理内置角色与库中全部启用角色的权限缓存
        for (com.qualitest.project.enums.TestProjectMemberRole seed
                : com.qualitest.project.enums.TestProjectMemberRole.values()) {
            if (seed == com.qualitest.project.enums.TestProjectMemberRole.SYS_ADMIN) {
                continue;
            }
            evictPermCache(seed.getCode());
        }
        List<TestProjectMemberRoleResult> all = testProjectMemberRoleMapper.selectTestProjectMemberRoleResultList(
                TestProjectMemberRoleParams.builder().delStatus(0L).build());
        if (all != null) {
            for (TestProjectMemberRoleResult row : all) {
                evictPermCache(row.getRoleKey());
            }
        }
    }

    /**
     * 新增角色并保存权限
     *
     * @param params 保存入参
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertRoleWithPerms(TestProjectMemberRoleSaveParams params) {
        validateSaveParams(params, true);
        assertRoleKeyUnique(params.getRoleKey(), null);
        TestProjectMemberRole role = TestProjectMemberRole.builder()
                .testProjectMemberRoleId(IdUtil.getSnowflakeNextId())
                .roleKey(params.getRoleKey().trim())
                .roleName(params.getRoleName().trim())
                .sortNum(params.getSortNum() == null ? 0L : params.getSortNum())
                .enableStatus(params.getEnableStatus() == null ? 1L : params.getEnableStatus())
                .delStatus(0L)
                .build();
        role.setRemark(params.getRemark());
        int rows = insertTestProjectMemberRole(role);
        replacePerms(role.getTestProjectMemberRoleId(), params.getPermCodes());
        evictPermCache(role.getRoleKey());
        return rows;
    }

    /**
     * 修改角色并全量替换权限；角色标识变更时同步更新成员上的 role_key
     *
     * @param params 保存入参
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateRoleWithPerms(TestProjectMemberRoleSaveParams params) {
        validateSaveParams(params, false);
        if (params.getTestProjectMemberRoleId() == null) {
            throw new ServiceException("请指定角色");
        }
        TestProjectMemberRole existing =
                testProjectMemberRoleMapper.selectTestProjectMemberRoleById(params.getTestProjectMemberRoleId());
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() == 1L)) {
            throw new ServiceException("角色不存在");
        }
        String newKey = params.getRoleKey().trim();
        assertRoleKeyUnique(newKey, params.getTestProjectMemberRoleId());
        String oldKey = existing.getRoleKey();
        TestProjectMemberRole toUpdate = TestProjectMemberRole.builder()
                .testProjectMemberRoleId(params.getTestProjectMemberRoleId())
                .roleKey(newKey)
                .roleName(params.getRoleName().trim())
                .sortNum(params.getSortNum() == null ? existing.getSortNum() : params.getSortNum())
                .enableStatus(params.getEnableStatus() == null ? existing.getEnableStatus() : params.getEnableStatus())
                .build();
        toUpdate.setRemark(params.getRemark());
        int rows = updateTestProjectMemberRole(toUpdate);
        replacePerms(params.getTestProjectMemberRoleId(), params.getPermCodes());
        if (!Objects.equals(oldKey, newKey)) {
            testProjectMemberMapper.updateMemberRoleKey(oldKey, newKey);
            evictPermCache(oldKey);
        }
        evictPermCache(newKey);
        return rows;
    }

    /**
     * 启用中的角色列表（成员下拉）
     *
     * @return 角色 Result 列表
     */
    @Override
    public List<TestProjectMemberRoleResult> listEnabledRoles() {
        return testProjectMemberRoleMapper.selectTestProjectMemberRoleResultList(
                TestProjectMemberRoleParams.builder()
                        .enableStatus(1L)
                        .delStatus(0L)
                        .build());
    }

    /**
     * 全量覆盖某角色的权限行（去重并校验权限 code）
     *
     * @param roleId 角色 id
     * @param permCodes 权限 code 列表
     */
    private void replacePerms(Long roleId, List<String> permCodes) {
        testProjectMemberRolePermMapper.deleteTestProjectMemberRolePermByRoleId(roleId);
        if (permCodes == null || permCodes.isEmpty()) {
            return;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String code : permCodes) {
            if (StrUtil.isBlank(code)) {
                continue;
            }
            String trimmed = code.trim();
            if (TestProjectMemberPerm.getByCode(trimmed) == null) {
                throw new ServiceException("未知权限: " + trimmed);
            }
            unique.add(trimmed);
        }
        for (String code : unique) {
            TestProjectMemberRolePerm row = TestProjectMemberRolePerm.builder()
                    .testProjectMemberRolePermId(IdUtil.getSnowflakeNextId())
                    .testProjectMemberRoleId(roleId)
                    .permCode(code)
                    .build();
            row.setCreateTime(DateUtils.getNowDate());
            testProjectMemberRolePermMapper.insertTestProjectMemberRolePerm(row);
        }
    }

    /**
     * 校验角色保存入参必填项与标识合法性
     *
     * @param params 保存入参
     * @param creating 是否为新增
     */
    private void validateSaveParams(TestProjectMemberRoleSaveParams params, boolean creating) {
        if (params == null) {
            throw new ServiceException("参数不能为空");
        }
        if (StrUtil.isBlank(params.getRoleKey())) {
            throw new ServiceException("请填写角色标识");
        }
        if (StrUtil.isBlank(params.getRoleName())) {
            throw new ServiceException("请填写角色名称");
        }
        if (com.qualitest.project.enums.TestProjectMemberRole.isSysAdmin(params.getRoleKey().trim())) {
            throw new ServiceException("角色标识不可使用 sysAdmin");
        }
        if (!creating && params.getTestProjectMemberRoleId() == null) {
            throw new ServiceException("请指定角色");
        }
    }

    /**
     * 校验未删除角色中 role_key 唯一
     *
     * @param roleKey 角色标识
     * @param excludeId 修改时排除的角色 id，可为 null
     */
    private void assertRoleKeyUnique(String roleKey, Long excludeId) {
        TestProjectMemberRole found = testProjectMemberRoleMapper.selectTestProjectMemberRoleOne(
                TestProjectMemberRoleParams.builder().roleKey(roleKey).delStatus(0L).build());
        if (found != null && (excludeId == null || !excludeId.equals(found.getTestProjectMemberRoleId()))) {
            throw new ServiceException("角色标识已存在: " + roleKey);
        }
    }

    /**
     * 校验角色可被逻辑删除（无成员仍引用该 role_key）
     *
     * @param roleId 角色 id
     */
    private void assertRoleDeletable(Long roleId) {
        TestProjectMemberRole existing = testProjectMemberRoleMapper.selectTestProjectMemberRoleById(roleId);
        if (existing == null) {
            throw new ServiceException("角色不存在");
        }
        int used = testProjectMemberMapper.countByMemberRole(existing.getRoleKey());
        if (used > 0) {
            throw new ServiceException("仍有 " + used + " 名成员使用该角色，请先调整成员角色后再删除");
        }
    }

    /**
     * 删除指定角色标识在 Redis 中的权限缓存
     *
     * @param roleKey 角色标识
     */
    private void evictPermCache(String roleKey) {
        if (StrUtil.isBlank(roleKey)) {
            return;
        }
        redisCache.deleteObject(
                CACHE_KEY_PREFIX + com.qualitest.project.enums.TestProjectMemberRole.normalizeCode(roleKey));
    }
}
