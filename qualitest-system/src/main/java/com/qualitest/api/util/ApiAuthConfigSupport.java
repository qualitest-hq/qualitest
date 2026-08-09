package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.common.exception.ServiceException;

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
     * 转为可写入 auth_config 列的 JSON。
     * <p>
     * 返回 null：上传包未带 auth，或 mode 为空 → 调用方应跳过覆盖。
     * 非法 mode：抛业务异常。
     *
     * @param auth 上传项中的鉴权对象
     * @return JSON 字符串，或 null
     */
    public static String toStorageJson(ApiAuthConfig auth) {
        if (auth == null || StrUtil.isBlank(auth.getMode())) {
            return null;
        }
        String mode = auth.getMode().trim();
        if (!isSupportedMode(mode)) {
            throw new ServiceException("不支持的 auth.mode: " + mode + "（允许 none/inherit/override）");
        }
        ApiAuthConfig normalized = ApiAuthConfig.builder()
                .mode(mode)
                .authProfileId(StrUtil.trimToNull(auth.getAuthProfileId()))
                .build();
        return JSONUtil.toJsonStr(normalized);
    }

    /**
     * 是否为已支持的鉴权模式。
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
