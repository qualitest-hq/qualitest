package com.qualitest.plugin.mount;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.manifest.PluginManifest;

import java.util.regex.Pattern;

/**
 * 按说明书 projectMount 校验与注入项目挂载。
 */
public final class ProjectMountSupport {

    private ProjectMountSupport() {
    }

    /**
     * 校验挂载 JSON 相对说明书字段声明；缺必填或 pattern 不匹配时抛 PROJECT_MOUNT_REQUIRED。
     *
     * @return 原 mountObj（已 trim 字符串字段）
     */
    public static JSONObject requireValidMount(PluginManifest.ProjectMountSpec spec, JSONObject mountObj) {
        if (spec == null) {
            return mountObj == null ? new JSONObject() : mountObj;
        }
        JSONObject obj = mountObj == null ? new JSONObject() : mountObj;
        for (PluginManifest.MountField field : spec.getFields()) {
            Object raw = obj.get(field.getName());
            String text = raw == null ? "" : String.valueOf(raw).trim();
            if (field.isRequired() && text.isBlank()) {
                throw new PluginException(PluginErrorCode.PROJECT_MOUNT_REQUIRED,
                        "请先在项目设置配置挂载字段: " + field.getName());
            }
            if (!text.isBlank() && field.getPattern() != null && !field.getPattern().isBlank()) {
                if (!Pattern.compile(field.getPattern()).matcher(text).matches()) {
                    throw new PluginException(PluginErrorCode.PROJECT_MOUNT_REQUIRED,
                            "挂载字段 " + field.getName() + " 格式不合法");
                }
            }
            if (raw instanceof String) {
                obj.put(field.getName(), text);
            }
        }
        return obj;
    }

    /**
     * 按 inject 声明把挂载字段写入环境 config。
     */
    public static void applyMountToConfig(JSONObject config, JSONObject mountObj, PluginManifest.ProjectMountSpec spec) {
        if (config == null || mountObj == null || spec == null) {
            return;
        }
        for (PluginManifest.MountInject inj : spec.getInject()) {
            if (!mountObj.containsKey(inj.getFrom())) {
                continue;
            }
            Object value = mountObj.get(inj.getFrom());
            if (value == null) {
                continue;
            }
            if (value instanceof String s && s.isBlank()) {
                continue;
            }
            putByPath(config, inj.getTo(), value);
        }
    }

    /** 按点分路径写入 JSON 对象。 */
    public static void putByPath(JSONObject root, String path, Object value) {
        if (root == null || path == null || path.isBlank()) {
            return;
        }
        String[] parts = path.split("\\.");
        JSONObject cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String key = parts[i];
            JSONObject next = cur.getJSONObject(key);
            if (next == null) {
                next = new JSONObject();
                cur.put(key, next);
            }
            cur = next;
        }
        cur.put(parts[parts.length - 1], value);
    }
}
