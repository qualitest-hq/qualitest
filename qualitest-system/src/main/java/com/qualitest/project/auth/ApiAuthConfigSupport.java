package com.qualitest.project.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ApiAuthConfig.Header;
import com.qualitest.common.exception.ServiceException;

import java.util.Locale;

/**
 * 接口鉴权标签的校验、解析与落库序列化。
 * <p>
 * {@code auth.mode} 必填，仅允许 none / inherit / override；空白或非法值一律抛错，不会改写成 inherit。
 * 接口行只存 mode、可选 authProfileId、override 时的头模板；凭证占位写在项目 Profile 托管头上。
 */
public final class ApiAuthConfigSupport {

    private ApiAuthConfigSupport() {}

    /**
     * 解析接口鉴权 JSON。空白、缺 mode 或非法 JSON 抛业务异常。
     */
    public static ApiAuthConfig parse(String json) {
        if (StrUtil.isBlank(json)) {
            throw new ServiceException("接口鉴权配置不能为空，须含 auth.mode（none/inherit/override）");
        }
        try {
            ApiAuthConfig cfg = JSONUtil.toBean(json, ApiAuthConfig.class);
            if (cfg == null || StrUtil.isBlank(cfg.getMode())) {
                throw new ServiceException("接口鉴权配置须含 auth.mode（none/inherit/override）");
            }
            String mode = canonicalizeMode(cfg.getMode());
            if (mode == null) {
                throw new ServiceException("不支持的 auth.mode: " + cfg.getMode() + "（允许 none/inherit/override）");
            }
            cfg.setMode(mode);
            return cfg;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("接口鉴权配置不是合法 JSON");
        }
    }

    /**
     * 规范化接口鉴权 JSON 并写库。缺 mode 或非法则抛业务异常。
     */
    public static String normalizeToJson(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new ServiceException("接口鉴权配置不能为空，须含 auth.mode（none/inherit/override）");
        }
        ApiAuthConfig parsed;
        try {
            parsed = JSONUtil.toBean(raw.trim(), ApiAuthConfig.class);
        } catch (Exception e) {
            throw new ServiceException("接口鉴权配置不是合法 JSON");
        }
        return toStorageJson(parsed);
    }

    /**
     * 转成可写入接口 auth_config 列的 JSON。
     * auth 或 mode 为空、非法 mode、override 缺头时抛业务异常。
     */
    public static String toStorageJson(ApiAuthConfig auth) {
        if (auth == null || StrUtil.isBlank(auth.getMode())) {
            throw new ServiceException("接口鉴权配置须含 auth.mode（none/inherit/override）");
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

    /** 落库用的 {"mode":"none"}。 */
    public static String noneStorageJson() {
        return JSONUtil.toJsonStr(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_NONE).build());
    }

    /** 落库用的 {"mode":"inherit"}。 */
    public static String inheritStorageJson() {
        return JSONUtil.toJsonStr(ApiAuthConfig.builder().mode(ApiAuthConfig.MODE_INHERIT).build());
    }

    /**
     * 导入时写入接口鉴权：上传包必须带 mode，按上传值落库，不会因预制列表改成 none。
     * inherit 且上传未带 authProfileId 时，用 inheritProfileId 回填；none 时清空 profileId。
     *
     * @param incoming         上传包中的鉴权对象，须含 mode
     * @param inheritProfileId inherit 且上传未指定 profile 时回填的 Profile id
     * @return 可写入接口 auth_config 的 JSON
     */
    public static String applyOnImport(ApiAuthConfig incoming, String inheritProfileId) {
        if (incoming == null || StrUtil.isBlank(incoming.getMode())) {
            throw new ServiceException("导入接口须带 auth.mode（none/inherit/override）");
        }
        String mode = canonicalizeMode(incoming.getMode());
        if (mode == null) {
            throw new ServiceException("不支持的 auth.mode: " + incoming.getMode() + "（允许 none/inherit/override）");
        }
        String profileId = StrUtil.trimToNull(incoming.getAuthProfileId());
        if (ApiAuthConfig.MODE_INHERIT.equals(mode) && profileId == null) {
            profileId = StrUtil.trimToNull(inheritProfileId);
        }
        if (ApiAuthConfig.MODE_NONE.equals(mode)) {
            profileId = null;
        }
        ApiAuthConfig.ApiAuthConfigBuilder builder = ApiAuthConfig.builder()
                .mode(mode)
                .authProfileId(profileId);
        if (ApiAuthConfig.MODE_OVERRIDE.equals(mode)) {
            builder.header(incoming.getHeader());
        }
        return toStorageJson(builder.build());
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
