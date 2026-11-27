package com.qualitest.project.auth;

import cn.hutool.core.util.StrUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.project.auth.AuthHeaderResolver.ResolvedAuthHeader;
import com.qualitest.project.result.ManagedAuthHeader;

/**
 * 按接口鉴权标签和项目鉴权配置，算出调试台要展示的托管头。
 * <p>
 * 内部委托 {@link AuthHeaderResolver}：接口 mode=none、未命中 Profile、头模板不完整时返回 null。
 * 返回对象带上头名、值模板、来源（profile / override）及 Profile 显示名。
 */
public final class ManagedAuthHeaderSupport {

    /** 头来自项目 Profile 模板 */
    public static final String SOURCE_PROFILE = "profile";

    /** 头来自接口 override 自定义模板 */
    public static final String SOURCE_OVERRIDE = "override";

    private ManagedAuthHeaderSupport() {
    }

    /**
     * 计算调试台展示用的托管头。
     * 接口 auth.mode=none 时返回 null；inherit / override 时按解析结果组装。
     */
    public static ManagedAuthHeader resolve(String apiAuthJson, String projectAuthJson, String apiPath) {
        ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(apiAuthJson, projectAuthJson, apiPath);
        if (resolved == null || resolved.skipped() || StrUtil.isBlank(resolved.name())) {
            return null;
        }
        ApiAuthConfig apiAuth = ApiAuthConfigSupport.parse(apiAuthJson);
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
