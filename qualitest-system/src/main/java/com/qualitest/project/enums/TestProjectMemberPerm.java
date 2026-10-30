package com.qualitest.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测试项目成员权限目录项，供角色权限配置与门禁校验使用。
 *
 * @author qualitest
 */
@Getter
@AllArgsConstructor
public enum TestProjectMemberPerm {

    /** 编辑项目设置 */
    PROJECT_EDIT("project:edit", "编辑项目设置", "项目"),
    /** 删除项目 */
    PROJECT_DELETE("project:delete", "删除项目", "项目"),
    /** 转让项目所有者 */
    PROJECT_TRANSFER("project:transfer", "转让所有者", "项目"),

    /** 查看成员列表 */
    MEMBER_VIEW("member:view", "查看成员", "成员"),
    /** 添加项目成员 */
    MEMBER_ADD("member:add", "添加成员", "成员"),
    /** 编辑成员角色等信息 */
    MEMBER_EDIT("member:edit", "编辑成员", "成员"),
    /** 移除项目成员 */
    MEMBER_REMOVE("member:remove", "移除成员", "成员"),

    /** 查看接口与分组 */
    API_VIEW("api:view", "查看接口", "接口"),
    /** 新增接口 */
    API_ADD("api:add", "新增接口", "接口"),
    /** 编辑接口与分组 */
    API_EDIT("api:edit", "编辑接口与分组", "接口"),
    /** 删除接口 */
    API_REMOVE("api:remove", "删除接口", "接口"),

    /** 查看测试流与跑流记录 */
    FLOW_VIEW("flow:view", "查看测试流与跑流记录", "测试流"),
    /** 新建测试流 */
    FLOW_ADD("flow:add", "新建测试流", "测试流"),
    /** 编辑测试流与分组 */
    FLOW_EDIT("flow:edit", "编辑测试流与分组", "测试流"),
    /** 删除测试流 */
    FLOW_REMOVE("flow:remove", "删除测试流", "测试流"),
    /** 执行测试流 */
    FLOW_RUN("flow:run", "执行测试流", "测试流"),
    /** 测试流节点发起外联 HTTP */
    FLOW_EXTERNAL("flow:external", "测试流外联 HTTP", "测试流"),

    /** 查看套件与运行记录 */
    SUITE_VIEW("suite:view", "查看套件与运行记录", "套件"),
    /** 新建测试套件 */
    SUITE_ADD("suite:add", "新建套件", "套件"),
    /** 编辑测试套件 */
    SUITE_EDIT("suite:edit", "编辑套件", "套件"),
    /** 删除测试套件 */
    SUITE_REMOVE("suite:remove", "删除套件", "套件"),
    /** 执行测试套件 */
    SUITE_RUN("suite:run", "执行套件", "套件"),

    /** 查看项目环境 */
    ENV_VIEW("env:view", "查看环境", "环境素材"),
    /** 编辑项目环境 */
    ENV_EDIT("env:edit", "编辑环境", "环境素材"),
    /** 查看项目素材 */
    ASSET_VIEW("asset:view", "查看素材", "环境素材"),
    /** 编辑项目素材 */
    ASSET_EDIT("asset:edit", "编辑素材", "环境素材"),

    /** 接口工作台使用 AI */
    AI_API_DESIGN("ai:api:design", "接口工作台 AI", "AI"),
    /** 造流页使用 AI 助手 */
    AI_FLOW_DESIGN("ai:flow:design", "造流 AI 助手", "AI"),
    /** 造流页 AI 全自动写入 */
    AI_FLOW_WRITE("ai:flow:write", "造流 AI 全自动落盘", "AI"),

    /** 使用项目 Token */
    TOKEN_USE("token:use", "使用项目 Token", "Token / 插件 / MCP"),
    /** IDEA 插件上传接口 */
    PLUGIN_API_IMPORT("plugin:api:import", "IDEA 插件上传接口", "Token / 插件 / MCP"),
    /** MCP 只读类工具 */
    MCP_READ("mcp:read", "MCP 只读工具", "Token / 插件 / MCP"),
    /** MCP 写入测试流 */
    MCP_FLOW_WRITE("mcp:flow:write", "MCP 写流", "Token / 插件 / MCP"),
    /** MCP 导入接口 */
    MCP_API_IMPORT("mcp:api:import", "MCP 导入接口", "Token / 插件 / MCP"),
    /** MCP 执行测试流 */
    MCP_RUN("mcp:run", "MCP 跑流", "Token / 插件 / MCP");

    /** 权限编码，门禁与持久化均用此值 */
    private final String code;

    /** 管理端展示名称 */
    private final String name;

    /** 父级分组标题（勾选树父节点） */
    private final String group;

    /**
     * 按 code 解析；未知则 null
     *
     * @param code 权限编码
     * @return 枚举或 null
     */
    public static TestProjectMemberPerm getByCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (TestProjectMemberPerm perm : values()) {
            if (perm.code.equals(code)) {
                return perm;
            }
        }
        return null;
    }

    /**
     * 管理端勾选树：父节点为分组，子节点为具体权限。
     * 父节点 id 形如 g:项目，子节点 id 为权限 code。
     *
     * @return 树根列表，每项含 id、label、children
     */
    public static List<Map<String, Object>> treeCatalog() {
        Map<String, List<TestProjectMemberPerm>> byGroup = new LinkedHashMap<>();
        for (TestProjectMemberPerm perm : values()) {
            byGroup.computeIfAbsent(perm.group, g -> new ArrayList<>()).add(perm);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map.Entry<String, List<TestProjectMemberPerm>> e : byGroup.entrySet()) {
            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put("id", "g:" + e.getKey());
            parent.put("label", e.getKey());
            List<Map<String, Object>> children = new ArrayList<>();
            for (TestProjectMemberPerm perm : e.getValue()) {
                Map<String, Object> child = new LinkedHashMap<>();
                child.put("id", perm.getCode());
                child.put("label", perm.getName());
                children.add(child);
            }
            parent.put("children", children);
            roots.add(parent);
        }
        return Collections.unmodifiableList(roots);
    }

    /**
     * 全部权限 code
     *
     * @return 枚举顺序下的 code 列表
     */
    public static List<String> allCodes() {
        return Arrays.stream(values()).map(TestProjectMemberPerm::getCode).collect(Collectors.toList());
    }
}
