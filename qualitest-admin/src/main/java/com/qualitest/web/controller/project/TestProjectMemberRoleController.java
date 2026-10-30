package com.qualitest.web.controller.project;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.params.TestProjectMemberRoleParams;
import com.qualitest.project.params.TestProjectMemberRoleSaveParams;
import com.qualitest.project.result.TestProjectMemberRoleResult;
import com.qualitest.project.service.ITestProjectMemberRoleService;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * 测试项目成员角色Controller
 *
 * @author qualitest
 * @date 2026-10-29
 */
@RestController
@RequestMapping("/project/testProjectMemberRole")
@AllArgsConstructor
public class TestProjectMemberRoleController extends BaseController {

    /** 项目成员角色业务 */
    private final ITestProjectMemberRoleService testProjectMemberRoleService;

    /**
     * 查询测试项目成员角色列表
     *
     * @param params 查询条件
     * @return 分页列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMemberRole:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectMemberRoleParams params) {
        startPage();
        List<TestProjectMemberRoleResult> list = testProjectMemberRoleService.selectTestProjectMemberRoleResultList(params);
        return getDataTable(list);
    }

    /**
     * 查询启用中的角色选项（成员表单下拉）
     *
     * @return 角色列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:list') or @ss.hasPermi('project:testProjectMemberRole:list')")
    @GetMapping("/enabledOptions")
    public R<List<TestProjectMemberRoleResult>> enabledOptions() {
        return ok(testProjectMemberRoleService.listEnabledRoles());
    }

    /**
     * 查询权限勾选目录树（分组为父节点，权限为子节点）
     *
     * @return 树形目录
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMemberRole:query') or @ss.hasPermi('project:testProjectMemberRole:add') or @ss.hasPermi('project:testProjectMemberRole:edit')")
    @GetMapping("/permCatalog")
    public R<List<Map<String, Object>>> permCatalog() {
        return ok(TestProjectMemberPerm.treeCatalog());
    }

    /**
     * 导出测试项目成员角色列表
     *
     * @param response 响应流
     * @param params 查询条件
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMemberRole:export')")
    @Log(title = "项目角色", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestProjectMemberRoleParams params) {
        List<TestProjectMemberRoleResult> list = testProjectMemberRoleService.selectTestProjectMemberRoleResultList(params);
        ExcelUtil<TestProjectMemberRoleResult> util = new ExcelUtil<>(TestProjectMemberRoleResult.class);
        util.exportExcel(response, list, "项目角色数据");
    }

    /**
     * 获取测试项目成员角色详细信息（含权限 code）
     *
     * @param testProjectMemberRoleId 角色主键
     * @return 角色详情
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMemberRole:query')")
    @GetMapping(value = "/{testProjectMemberRoleId}")
    public R<TestProjectMemberRoleResult> getInfo(@PathVariable("testProjectMemberRoleId") Long testProjectMemberRoleId) {
        return ok(testProjectMemberRoleService.selectTestProjectMemberRoleResult(testProjectMemberRoleId));
    }

    /**
     * 新增测试项目成员角色及权限
     *
     * @param params 保存入参
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMemberRole:add')")
    @Log(title = "项目角色", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestProjectMemberRoleSaveParams params) {
        return toR(testProjectMemberRoleService.insertRoleWithPerms(params));
    }

    /**
     * 修改测试项目成员角色及权限
     *
     * @param params 保存入参
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMemberRole:edit')")
    @Log(title = "项目角色", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProjectMemberRoleSaveParams params) {
        return toR(testProjectMemberRoleService.updateRoleWithPerms(params));
    }

    /**
     * 删除测试项目成员角色
     *
     * @param testProjectMemberRoleIds 角色主键数组
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMemberRole:remove')")
    @Log(title = "项目角色", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testProjectMemberRoleIds}")
    public R<Void> remove(@PathVariable Long[] testProjectMemberRoleIds) {
        List<Long> idList = Convert.toLongList(testProjectMemberRoleIds);
        return toR(testProjectMemberRoleService.logicDeleteTestProjectMemberRoleByIdList(idList));
    }
}
