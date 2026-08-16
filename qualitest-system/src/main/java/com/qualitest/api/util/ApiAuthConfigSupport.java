package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ApiAuthConfig.Header;
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
     * 解析接口鉴权 JSON；空白或非法时按 {@code inherit}（未标注视为需登录）。
     * 造流补头、工具摘要等共用，勿各写一份默认逻辑。
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
     * 规范化用户提交的接口鉴权 JSON 并写库。
     * <p>
     * 空白 → 默认 inherit；非法 JSON / 规则失败 → {@link ServiceException}。
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
     * 转为可写入 auth_config 列的 JSON。
     * <p>
     * 返回 null：上传包未带 auth，或 mode 为空 → 调用方应跳过覆盖。
     * 非法 mode / override 缺头：抛业务异常。
     *
     * @param auth 上传项中的鉴权对象
     * @return JSON 字符串，或 null
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

    /**
     * 落库用的 {@code {"mode":"none"}}；不应返回 null。
     */
    public static String noneStorageJson() {
        return JSONUtil.toJsonStr(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_NONE).build());
    }

    /**
     * 内置免登 path 上若仍为 inherit（或等价空），改为 none；override / 已是 none 不改。
     * 导入与单条增改共用，避免两处各写一遍。
     */
    public static String coerceInheritToNoneIfBuiltinPath(String authJson, String apiPath) {
        if (!ProjectAuthConfigSupport.matchesBuiltinAnonymousAuthPath(apiPath)) {
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
