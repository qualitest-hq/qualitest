package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ApiAuthConfig.Header;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.common.exception.ServiceException;

import java.util.Locale;

/**
 * 接口鉴权标签的校验、解析与落库序列化。
 * <p>
 * 导入接口时把上传包里的 auth 对象转成 JSON 字符串写入数据库；
 * 未声明或 mode 为空时返回 null，表示本次导入不要改库里已有值。
 */
public final class ApiAuthConfigSupport {

    private ApiAuthConfigSupport() {}

    /**
     * 解析接口鉴权 JSON。空白或非法按 inherit（未标注视为要登录）。
     */
    public static ApiAuthConfig parseOrInherit(String json) {
        if (StrUtil.isBlank(json)) {
            return ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_INHERIT).build();
        }
        try {
            ApiAuthConfig cfg = JSONUtil.toBean(json, ApiAuthConfig.class);
            if (cfg == null || StrUtil.isBlank(cfg.getMode())) {
                return ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_INHERIT).build();
            }
            return cfg;
        } catch (Exception e) {
            return ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_INHERIT).build();
        }
    }

    /**
     * 规范化接口鉴权 JSON 并写库。空白写成 inherit；非法 JSON 或规则失败抛业务异常。
     */
    public static String normalizeToJson(String raw) {
        if (StrUtil.isBlank(raw)) {
            return JSONUtil.toJsonStr(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_INHERIT).build());
        }
        ApiAuthConfig parsed;
        try {
            parsed = JSONUtil.toBean(raw.trim(), ApiAuthConfig.class);
        } catch (Exception e) {
            throw new ServiceException("接口鉴权配置不是合法 JSON");
        }
        if (parsed == null || StrUtil.isBlank(parsed.getMode())) {
            parsed = ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_INHERIT).build();
        }
        String stored = toStorageJson(parsed);
        if (stored == null) {
            return JSONUtil.toJsonStr(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_INHERIT).build());
        }
        return stored;
    }

    /**
     * 转成可写入接口 auth_config 列的 JSON。
     * 未带 auth 或 mode 为空返回 null，调用方应跳过覆盖。
     * 非法 mode、override 缺头时抛业务异常。
     */
    public static String toStorageJson(ApiAuthConfig auth) {
        if (auth == null || StrUtil.isBlank(auth.getMode())) {
            return null;
        }
        String mode = canonicalizeMode(auth.getMode());
        if (mode == null) {
            throw new ServiceException("不支持的 auth.mode: " + auth.getMode() + "（允许 none/inherit/override）");
        }
        ApiAuthConfig.ApiAuthConfigBuilder builder = ApiAuthConfig.builder().mode(mode);
        if (ApiAuthConfig.MODE_OVERRIDE.equals(mode)) {
            Header header = normalizeOverrideHeader(auth.getHeader());
            builder.header(header);
        } else if (ApiAuthConfig.MODE_INHERIT.equals(mode)) {
            builder.authProfileId(StrUtil.trimToNull(auth.getAuthProfileId()));
        }
        if (auth.getLoginHint() != null) {
            builder.loginHint(ProjectAuthConfigSupport.normalizeLoginHint(auth.getLoginHint(), "authConfig"));
        }
        return JSONUtil.toJsonStr(builder.build());
    }

    /**
     * 校验并清理 override 头模板。
     */
    private static Header normalizeOverrideHeader(Header header) {
        String name = header != null ? StrUtil.trimToNull(header.getName()) : null;
        String valueTemplate = header != null ? StrUtil.trimToNull(header.getValueTemplate()) : null;
        if (name == null || valueTemplate == null) {
            throw new ServiceException("auth.mode=override 须配置 header.name 与 header.valueTemplate");
        }
        return Header.builder().name(name).valueTemplate(valueTemplate).build();
    }

    /** 落库用的 {"mode":"none"}。 */
    public static String noneStorageJson() {
        return JSONUtil.toJsonStr(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_NONE).build());
    }

    /**
     * inherit 且该接口是免登口时改成 none。override 和已是 none 不改。
     * 项目有 Profile 时只认预制 mode=none；配置空才用内置 /login 等路径。
     */
    public static String coerceInheritToNoneIfAnonymous(
            String authJson, String apiPath, String method, String projectAuthJson) {
        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
        if (!ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(method, apiPath, projectAuth)) {
            return authJson;
        }
        ApiAuthConfig parsed = parseOrInherit(authJson);
        if (!ApiAuthConfig.MODE_INHERIT.equalsIgnoreCase(StrUtil.trim(parsed.getMode()))) {
            return authJson;
        }
        return noneStorageJson();
    }

    /**
     * 规范为小写 mode；不支持则返回 null。
     */
    public static String canonicalizeMode(String mode) {
        if (StrUtil.isBlank(mode)) {
            return null;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return isSupportedMode(normalized) ? normalized : null;
    }

    /**
     * 是否为已支持的鉴权模式（须已是小写规范值）。
     */
    public static boolean isSupportedMode(String mode) {
        if (mode == null) {
            return false;
        }
        return ApiAuthConfig.MODE_NONE.equals(mode)
                || ApiAuthConfig.MODE_INHERIT.equals(mode)
                || ApiAuthConfig.MODE_OVERRIDE.equals(mode);
    }
}
