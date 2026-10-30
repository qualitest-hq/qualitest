package com.qualitest.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内置与虚拟的项目成员角色标识，成员记录以 role_key 存储。
 *
 * @author qualitest
 */
@Getter
@AllArgsConstructor
public enum TestProjectMemberRole {

    /** 平台超管在项目内的虚拟角色 */
    SYS_ADMIN("sysAdmin", "系统管理员"),
    /** 项目所有者 */
    OWNER("owner", "所有者"),
    /** 项目管理员 */
    ADMIN("admin", "管理员"),
    /** 后端开发 */
    BACKEND("backend", "后端开发者"),
    /** 前端开发 */
    FRONTEND("frontend", "前端开发者"),
    /** 测试人员 */
    TESTER("tester", "测试员");

    /** 角色标识，写入成员表 member_role */
    private final String code;

    /** 角色展示名称 */
    private final String name;

    /**
     * 规范化角色 code：旧值 developer 视为 backend；未知自定义 code 原样返回。
     *
     * @param code 原始 role_key
     * @return 规范化后的 role_key
     */
    public static String normalizeCode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        if ("developer".equals(code)) {
            return BACKEND.code;
        }
        return code;
    }

    /**
     * 按 code 解析内置或虚拟角色；未知 code 返回 null。
     *
     * @param code 角色标识
     * @return 匹配枚举或 null
     */
    public static TestProjectMemberRole getByCodeOrNull(String code) {
        String normalized = normalizeCode(code);
        if (normalized == null) {
            return null;
        }
        for (TestProjectMemberRole memberRole : values()) {
            if (memberRole.code.equals(normalized)) {
                return memberRole;
            }
        }
        return null;
    }

    /**
     * 按 code 解析内置或虚拟角色；未知 code 抛出非法参数异常。
     *
     * @param code 角色标识
     * @return 枚举常量
     * @deprecated 未知自定义 role_key 时请改用可空解析
     */
    @Deprecated
    public static TestProjectMemberRole getByCode(String code) {
        TestProjectMemberRole role = getByCodeOrNull(code);
        if (role == null) {
            throw new IllegalArgumentException("Invalid member role code: " + code);
        }
        return role;
    }

    /**
     * 是否所有者角色标识
     *
     * @param memberRoleCode 成员 role_key
     * @return code 为 owner 时为 true
     */
    public static boolean isOwner(String memberRoleCode) {
        return OWNER.code.equals(normalizeCode(memberRoleCode));
    }

    /**
     * 是否管理员角色标识
     *
     * @param memberRoleCode 成员 role_key
     * @return code 为 admin 时为 true
     */
    public static boolean isAdmin(String memberRoleCode) {
        return ADMIN.code.equals(normalizeCode(memberRoleCode));
    }

    /**
     * 是否系统管理员虚拟角色
     *
     * @param memberRoleCode 成员 role_key
     * @return code 为 sysAdmin 时为 true
     */
    public static boolean isSysAdmin(String memberRoleCode) {
        return SYS_ADMIN.code.equals(memberRoleCode);
    }
}
