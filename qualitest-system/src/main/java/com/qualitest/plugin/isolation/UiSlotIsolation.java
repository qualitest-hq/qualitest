package com.qualitest.plugin.isolation;

import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.manifest.PluginManifest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 启用侧 UI 槽互斥：仅比对 isolation 中 ui-slot: 前缀；异 pluginId 同槽则冲突。
 */
public final class UiSlotIsolation {

    private static final String PREFIX = "ui-slot:";

    private UiSlotIsolation() {
    }

    /** 从说明书取出有效 ui-slot 标签（完整字符串）。 */
    public static List<String> slotsOf(PluginManifest manifest) {
        List<String> out = new ArrayList<>();
        if (manifest == null) {
            return out;
        }
        for (String tag : manifest.getIsolation()) {
            if (isUiSlot(tag)) {
                out.add(tag);
            }
        }
        return out;
    }

    public static boolean isUiSlot(String tag) {
        return tag != null && tag.startsWith(PREFIX) && tag.length() > PREFIX.length();
    }

    /**
     * 校验待启用插件的槽是否与已启用的其它插件撞车。
     *
     * @param enablingPluginId 待启用插件 id
     * @param mySlots          待启用包的 ui-slot 列表
     * @param enabledSlotsByPlugin 已启用 pluginId → 其 ui-slot 列表（可含自身旧绑定）
     */
    public static void requireFree(
            String enablingPluginId,
            Collection<String> mySlots,
            Map<String, ? extends Collection<String>> enabledSlotsByPlugin) {
        if (mySlots == null || mySlots.isEmpty() || enabledSlotsByPlugin == null || enabledSlotsByPlugin.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ? extends Collection<String>> e : enabledSlotsByPlugin.entrySet()) {
            String otherId = e.getKey();
            if (otherId == null || otherId.equals(enablingPluginId)) {
                continue;
            }
            Collection<String> otherSlots = e.getValue();
            if (otherSlots == null) {
                continue;
            }
            for (String slot : otherSlots) {
                if (slot != null && mySlots.contains(slot)) {
                    throw new PluginException(PluginErrorCode.ISOLATION_MUTEX,
                            "UI 槽位已被占用: " + slot + "（启用中: " + otherId + "）");
                }
            }
        }
    }
}
