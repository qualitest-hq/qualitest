package com.qualitest.flow.http;

import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.ResponseConventionSupport;
import com.qualitest.project.support.TestProjectApiBizCodeService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP 节点业务成功码校验解析器。
 * <p>
 * 根据节点 successCheck、调用模式、接口级白名单与本端响应约定，
 * 决定本步是否校验 body 中的业务 code，以及成功值列表与字段路径。
 * <ul>
 *   <li>mode=off：不校验业务码，仅依赖 HTTP 状态码</li>
 *   <li>外联模式且未写 mode：视为关闭校验</li>
 *   <li>project 模式缺省或 mode=inherit：开启校验</li>
 *   <li>成功值优先级：节点 successCheck.successValues 非空 → 接口 biz_code_config → 本端约定 successValues（缺省 [200]）</li>
 *   <li>业务码 / 消息字段路径取自本端约定（缺省 code / msg）</li>
 * </ul>
 */
public final class SuccessCheckResolver {

    /** 继承本端响应约定并校验业务码 */
    public static final String MODE_INHERIT = "inherit";
    /** 关闭业务码校验 */
    public static final String MODE_OFF = "off";

    private SuccessCheckResolver() {
    }

    /**
     * 解析本 HTTP 节点是否做业务码校验，以及校验用的路径与成功值。
     *
     * @param nodeData               节点 data（可读 successCheck）
     * @param callMode               project / external
     * @param api                    绑定的项目接口；external 或未绑定时为 null
     * @param responseConventionJson 本端响应约定 JSON；空白时按代码缺省约定解析
     * @return 解析结果；shouldApply 为 false 时跳过业务码校验
     */
    public static Resolved resolve(Map<String, Object> nodeData, String callMode, TestProjectApi api,
                                   String responseConventionJson) {
        String mode = readMode(nodeData);
        if (mode == null || mode.isBlank()) {
            // 外联默认不校验；项目接口默认继承约定
            mode = FlowHttpCallMode.isExternal(callMode) ? MODE_OFF : MODE_INHERIT;
        }
        mode = mode.trim().toLowerCase();
        if (MODE_OFF.equals(mode)) {
            return Resolved.off();
        }

        ResponseConventionSupport.Parsed convention =
                ResponseConventionSupport.parseOrDefault(responseConventionJson);
        List<Integer> successValues = convention.successValues();

        List<Integer> nodeValues = readNodeSuccessValues(nodeData);
        if (nodeValues != null && !nodeValues.isEmpty()) {
            successValues = List.copyOf(nodeValues);
        } else if (api != null) {
            List<Integer> apiValues = TestProjectApiBizCodeService.readSuccessValues(api);
            if (apiValues != null && !apiValues.isEmpty()) {
                successValues = List.copyOf(apiValues);
            }
        }

        return new Resolved(true, convention.codePath(), convention.messagePath(), successValues);
    }

    /**
     * 节点是否关闭业务码校验（successCheck.mode=off）。
     * 没写 mode 时返回 false。
     */
    public static boolean isOff(Map<String, Object> nodeData) {
        String mode = readMode(nodeData);
        return mode != null && MODE_OFF.equalsIgnoreCase(mode.trim());
    }

    /** 读取节点 data.successCheck.mode */
    private static String readMode(Map<String, Object> nodeData) {
        Object successCheck = readSuccessCheck(nodeData);
        if (successCheck instanceof Map<?, ?> map) {
            Object mode = map.get("mode");
            return mode != null ? String.valueOf(mode) : null;
        }
        return null;
    }

    /**
     * 读取节点 data.successCheck.successValues；无或空则返回 null。
     * 支持 number / 可解析整数的字符串。
     */
    static List<Integer> readNodeSuccessValues(Map<String, Object> nodeData) {
        Object successCheck = readSuccessCheck(nodeData);
        if (!(successCheck instanceof Map<?, ?> map)) {
            return null;
        }
        Object raw = map.get("successValues");
        if (raw == null) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        if (raw instanceof Collection<?> coll) {
            for (Object item : coll) {
                Integer v = toInteger(item);
                if (v != null) {
                    out.add(v);
                }
            }
        } else if (raw instanceof Object[] arr) {
            for (Object item : arr) {
                Integer v = toInteger(item);
                if (v != null) {
                    out.add(v);
                }
            }
        } else {
            Integer single = toInteger(raw);
            if (single != null) {
                out.add(single);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static Object readSuccessCheck(Map<String, Object> nodeData) {
        if (nodeData == null) {
            return null;
        }
        return nodeData.get("successCheck");
    }

    /**
     * 判断实际业务码是否落在成功白名单内。
     * 支持数字与字符串互比（如 200 与 {@code "200"}）。
     */
    private static boolean isSuccess(Object actualCode, List<Integer> successValues) {
        if (actualCode == null || successValues == null || successValues.isEmpty()) {
            return false;
        }
        Integer actual = toInteger(actualCode);
        String actualStr = actual != null ? String.valueOf(actual) : String.valueOf(actualCode).trim();
        for (Integer expected : successValues) {
            if (expected == null) {
                continue;
            }
            if (actual != null && Objects.equals(actual, expected)) {
                return true;
            }
            if (actualStr.equals(String.valueOf(expected))) {
                return true;
            }
        }
        return false;
    }

    /** 将响应中的 code 转为 Integer；无法解析则返回 null */
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
     * 单次 HTTP 步骤的业务码校验配置快照。
     */
    public static final class Resolved {
        /** 是否执行业务码校验 */
        private final boolean apply;
        /** body 中业务码字段路径（相对根对象，如 code） */
        private final String codePath;
        /** body 中错误消息字段路径（如 msg） */
        private final String messagePath;
        /** 视为成功的业务码列表 */
        private final List<Integer> successValues;

        private Resolved(boolean apply, String codePath, String messagePath, List<Integer> successValues) {
            this.apply = apply;
            this.codePath = codePath;
            this.messagePath = messagePath;
            this.successValues = successValues != null ? List.copyOf(successValues) : List.of();
        }

        /** 关闭校验时的空配置 */
        static Resolved off() {
            return new Resolved(false, null, null, List.of());
        }

        /** 是否需要做业务码校验 */
        public boolean shouldApply() {
            return apply;
        }

        /** 业务码字段路径 */
        public String getCodePath() {
            return codePath;
        }

        /** 错误消息字段路径 */
        public String getMessagePath() {
            return messagePath;
        }

        /** 实际业务码是否成功 */
        public boolean isSuccess(Object actualCode) {
            return SuccessCheckResolver.isSuccess(actualCode, successValues);
        }

        /** 写入步骤报告用的成功值（不可变） */
        public List<Integer> successValuesForReport() {
            return successValues;
        }
    }
}
