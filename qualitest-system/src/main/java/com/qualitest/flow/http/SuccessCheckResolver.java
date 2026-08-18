package com.qualitest.flow.http;

import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.ResponseConventionSupport;
import com.qualitest.project.support.TestProjectApiBizCodeService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP 节点业务成功码校验解析器。
 * <p>
 * 根据节点 successCheck.mode、调用模式、接口级白名单与项目响应约定，
 * 决定本步是否校验 body 中的业务 code，以及成功值列表与字段路径。
 * <ul>
 *   <li>mode=off：不校验业务码，仅依赖 HTTP 状态码</li>
 *   <li>外联模式且未写 mode：视为关闭校验</li>
 *   <li>project 模式缺省或 mode=inherit：开启校验</li>
 *   <li>成功值：接口 biz_code_config.successValues 非空时优先，否则用项目约定（空则 [200]）</li>
 * </ul>
 */
public final class SuccessCheckResolver {

    /** 继承项目响应约定并校验业务码 */
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
     * @param responseConventionJson 项目 response_convention 原始 JSON；空则用默认约定
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
        // 接口级成功码白名单覆盖项目约定中的 successValues
        if (api != null) {
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
        if (nodeData == null) {
            return null;
        }
        Object raw = nodeData.get("successCheck");
        if (raw instanceof Map<?, ?> map) {
            Object mode = map.get("mode");
            return mode != null ? String.valueOf(mode) : null;
        }
        return null;
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
