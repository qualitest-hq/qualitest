package com.qualitest.api.util;

import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;

import java.util.List;

/**
 * 双端鉴权夹具（管理端在前 + 客户端），对齐「先勾管理端」。
 * 仅测试源码使用，生产已删除 dualBearerTemplate()。
 */
public final class AuthProfileTestFixtures {

    private AuthProfileTestFixtures() {}

    public static ProjectAuthConfig adminThenClient() {
        return ProjectAuthConfig.builder()
                .authProfiles(List.of(
                        ProjectAuthProfile.builder()
                                .id(ProjectAuthConfigSupport.PROFILE_ADMIN)
                                .name("管理端 Bearer")
                                .match(Match.builder()
                                        .pathPrefix(List.of("/system/", "/monitor/", "/tool/", "/web/"))
                                        .build())
                                .headerName("Authorization")
                                .headerValueTemplate("Bearer {{asset.adminAuth.token}}")
                                .credentialApi(ProjectAuthConfigSupport.credentialApi("POST", "/login"))
                                .apis(ProjectAuthConfigSupport.ruoyiBearerApis())
                                .build(),
                        ProjectAuthProfile.builder()
                                .id(ProjectAuthConfigSupport.PROFILE_CLIENT)
                                .name("客户端 Bearer")
                                .match(Match.builder().pathPrefix(List.of("/api/")).build())
                                .headerName("Authorization")
                                .headerValueTemplate("Bearer {{asset.clientAuth.token}}")
                                .credentialApi(ProjectAuthConfigSupport.credentialApi("POST", "/api/account/auth/login"))
                                .apis(ProjectAuthConfigSupport.clientBearerApis())
                                .build()
                ))
                .build();
    }

    public static String adminThenClientJson() {
        return ProjectAuthConfigSupport.toJson(adminThenClient());
    }
}
