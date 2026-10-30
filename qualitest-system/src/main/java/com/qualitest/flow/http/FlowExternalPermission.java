package com.qualitest.flow.http;

import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.project.enums.TestProjectMemberPerm;

import java.util.Collection;

/**
 * 判断当前用户是否允许在测试流中发起外联 HTTP 请求。
 *
 * @author qualitest
 */
public final class FlowExternalPermission {

    /** 平台菜单级权限字，用于未配置项目 flow:external 时的放行 */
    public static final String PERMISSION = "flow:http:external";

    /** 禁止实例化 */
    private FlowExternalPermission() {
    }

    /**
     * 根据项目成员权限集合与平台权限判断是否允许外联
     *
     * @param capabilities 项目成员权限 code 集合；为 null 时仅校验平台权限
     * @return 允许外联时为 true
     */
    public static boolean isPermitted(Collection<String> capabilities) {
        if (capabilities != null && capabilities.contains(TestProjectMemberPerm.FLOW_EXTERNAL.getCode())) {
            return true;
        }
        return SecurityUtils.hasPermi(PERMISSION);
    }
}
