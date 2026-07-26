package com.qualitest.flow.http;

import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.project.enums.TestProjectMemberRole;

/**
 * 外联 HTTP 与脚本 {@code ctx.http} 的运行权限判定。
 * <p>
 * 满足其一即可发起外联请求：当前用户为项目 owner/admin/sysAdmin，或拥有系统权限 {@link #PERMISSION}（{@code flow:http:external}）。
 */
public final class FlowExternalPermission {

    public static final String PERMISSION = "flow:http:external";

    private FlowExternalPermission() {
    }

    public static boolean isPermitted(TestProjectMemberRole memberRole) {
        if (memberRole == null) {
            return SecurityUtils.hasPermi(PERMISSION);
        }
        if (memberRole == TestProjectMemberRole.SYS_ADMIN
                || memberRole == TestProjectMemberRole.OWNER
                || memberRole == TestProjectMemberRole.ADMIN) {
            return true;
        }
        return SecurityUtils.hasPermi(PERMISSION);
    }
}
