package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.util.AuthHeaderResolver.ResolvedAuthHeader;
import com.qualitest.project.result.ManagedAuthHeader;

/**
 * 按接口鉴权标签和项目鉴权配置，算出调试台要展示的托管头。
 * 解析结果为跳过（免登录、未命中配置、模板不完整）时返回 null。
 */
public final class ManagedAuthHeaderSupport {

    /** 继承项目鉴权配置 */
    public static final String SOURCE_PROFILE = "profile";

    /** 接口自定义头 */
    public static final String SOURCE_OVERRIDE = "override";

    private ManagedAuthHeaderSupport() {
    }

    /**
     * 计算托管头。method 参与免登口判断，空则只按 path。
     */
    public static ManagedAuthHeader resolve(
            String apiAuthJson, String projectAuthJson, String apiPath, String method) {
        ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(apiAuthJson, projectAuthJson, apiPath, method);
        if (resolved == null || resolved.skipped() || StrUtil.isBlank(resolved.name())) {
            return null;
        }
        ApiAuthConfig apiAuth = ApiAuthConfigSupport.parseOrInherit(apiAuthJson);
        boolean override = ApiAuthConfig.MODE_OVERRIDE.equalsIgnoreCase(StrUtil.trim(apiAuth.getMode()));
        String profileName = null;
        if (!override && StrUtil.isNotBlank(resolved.profileId())) {
            profileName = ProjectAuthConfigSupport.displayProfileName(
                    ProjectAuthConfigSupport.parse(projectAuthJson), resolved.profileId());
        }
        return ManagedAuthHeader.builder()
                .name(resolved.name())
                .valueTemplate(resolved.valueTemplate())
                .authProfileId(resolved.profileId())
                .profileName(profileName)
                .source(override ? SOURCE_OVERRIDE : SOURCE_PROFILE)
                .build();
    }
}
