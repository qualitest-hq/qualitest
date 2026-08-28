package com.qualitest.flow.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP 节点状态码门禁解析器。
 * <p>
 * 与 {@link SuccessCheckResolver}（业务码）并列：先判 HTTP 状态，再在 2xx 后可选判业务码。
 * <ul>
 *   <li>mode=2xx（缺省）：仅 2xx 通过</li>
 *   <li>mode=whitelist：仅 status 落在 values 内通过（探活常用 [200,401]）</li>
 *   <li>mode=off：任意状态码通过（连接失败仍由上层处理）</li>
 * </ul>
 */
public final class StatusCheckResolver {

    /** 仅接受 2xx（默认） */
    public static final String MODE_2XX = "2xx";
    /** 仅接受 values 白名单 */
    public static final String MODE_WHITELIST = "whitelist";
    /** 不校验 HTTP 状态码 */
    public static final String MODE_OFF = "off";

    private StatusCheckResolver() {
    }

    /**
     * 解析节点 statusCheck；缺省或非法 mode 视为 2xx。
     *
     * @param nodeData 节点 data
     * @return 解析结果
     */
    public static Resolved resolve(Map<String, Object> nodeData) {
        String mode = readMode(nodeData);
        if (mode == null || mode.isBlank()) {
            return Resolved.twoXx();
        }
        mode = mode.trim().toLowerCase(Locale.ROOT);
        if (MODE_OFF.equals(mode)) {
            return Resolved.off();
        }
        if (MODE_WHITELIST.equals(mode)) {
            List<Integer> values = readValues(nodeData);
            if (values.isEmpty()) {
                // 白名单为空时退回严格 2xx，避免误开无限放行
                return Resolved.twoXx();
            }
            return Resolved.whitelist(values);
        }
        return Resolved.twoXx();
    }

    /** 当前状态码是否通过门禁 */
    public static boolean passes(Resolved resolved, int status) {
        if (resolved == null) {
            return status >= 200 && status < 300;
        }
        return resolved.passes(status);
    }

    private static String readMode(Map<String, Object> nodeData) {
        Object raw = readStatusCheck(nodeData);
        if (raw instanceof Map<?, ?> map) {
            Object mode = map.get("mode");
            return mode != null ? String.valueOf(mode) : null;
        }
        return null;
    }

    private static List<Integer> readValues(Map<String, Object> nodeData) {
        Object raw = readStatusCheck(nodeData);
        if (!(raw instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object valuesObj = map.get("values");
        if (!(valuesObj instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Object item : list) {
            Integer n = toInteger(item);
            if (n != null) {
                out.add(n);
            }
        }
        return List.copyOf(out);
    }

    private static Object readStatusCheck(Map<String, Object> nodeData) {
        if (nodeData == null) {
            return null;
        }
        return nodeData.get("statusCheck");
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 单次 HTTP 步骤的状态码门禁快照。
     */
    public static final class Resolved {
        private final String mode;
        private final List<Integer> values;

        private Resolved(String mode, List<Integer> values) {
            this.mode = mode;
            this.values = values != null ? List.copyOf(values) : List.of();
        }

        static Resolved twoXx() {
            return new Resolved(MODE_2XX, List.of());
        }

        static Resolved off() {
            return new Resolved(MODE_OFF, List.of());
        }

        static Resolved whitelist(List<Integer> values) {
            return new Resolved(MODE_WHITELIST, values);
        }

        /** 门禁模式 */
        public String getMode() {
            return mode;
        }

        /** whitelist 时的允许状态码；其它 mode 为空 */
        public List<Integer> getValues() {
            return values;
        }

        /** 状态码是否通过 */
        public boolean passes(int status) {
            if (MODE_OFF.equals(mode)) {
                return true;
            }
            if (MODE_WHITELIST.equals(mode)) {
                return values.contains(status);
            }
            return status >= 200 && status < 300;
        }
    }
}
