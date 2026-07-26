package com.qualitest.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 测试项目成员角色
 *
 * @author qualitest
 */
@Getter
@AllArgsConstructor
public enum TestProjectMemberRole {

    SYS_ADMIN("sysAdmin", "系统管理员"),
    OWNER("owner", "所有者"),
    ADMIN("admin", "管理员"),
    DEVELOPER("developer", "开发者"),
    TESTER("tester", "测试员");

    private final String code;

    private final String name;

    /**
     * 根据 code 获取枚举
     */
    public static TestProjectMemberRole getByCode(String code) {
        for (TestProjectMemberRole memberRole : TestProjectMemberRole.values()) {
            if (memberRole.code.equals(code)) {
                return memberRole;
            }
        }
        throw new IllegalArgumentException("Invalid member role code: " + code);
    }

    /**
     * 是否可管理项目成员
     */
    public static boolean canManageMember(String memberRoleCode) {
        if (memberRoleCode == null) {
            return false;
        }
        return OWNER.code.equals(memberRoleCode) || ADMIN.code.equals(memberRoleCode);
    }

    /**
     * 是否可使用项目 Token
     */
    public static boolean canUseProjectToken(String memberRoleCode) {
        if (memberRoleCode == null) {
            return false;
        }
        return OWNER.code.equals(memberRoleCode)
                || ADMIN.code.equals(memberRoleCode)
                || DEVELOPER.code.equals(memberRoleCode);
    }

}
