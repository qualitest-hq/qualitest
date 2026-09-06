package com.qualitest.flow.input;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 InputFieldTypes：字段类型规范化与 validateAndApply 写入 flow。
 * 边界：必填、select options、number、password 脱敏；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=InputFieldTypesTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InputFieldTypesTest {

    /**
     * 前提：text 必填 + number 可选，提交合法值。
     * 期望：flow 写入对应值；assigns 两条。
     */
    @Test
    @Order(1)
    @DisplayName("validateAndApply 写入 text 与 number")
    void validateAndApply_writesTextAndNumber() {
        List<Map<String, Object>> fields = List.of(
                Map.of("name", "captchaCode", "type", "text", "required", true),
                Map.of("name", "qty", "type", "number", "required", false)
        );
        Map<String, Object> flow = new HashMap<>();
        List<Map<String, Object>> assigns = InputFieldTypes.validateAndApply(
                fields, Map.of("captchaCode", "ab12", "qty", "3"), flow);

        assertEquals("ab12", flow.get("captchaCode"));
        assertEquals(3L, flow.get("qty"));
        assertEquals(2, assigns.size());
    }

    /**
     * 前提：必填字段未提交。
     * 期望：抛 TF_INPUT_INVALID。
     */
    @Test
    @Order(2)
    @DisplayName("必填缺失抛 TF_INPUT_INVALID")
    void validateAndApply_requiredMissing() {
        List<Map<String, Object>> fields = List.of(
                Map.of("name", "code", "type", "text", "required", true)
        );
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> InputFieldTypes.validateAndApply(fields, Map.of(), new HashMap<>()));
        assertEquals(FlowErrorCode.TF_INPUT_INVALID, ex.getErrorCode());
    }

    /**
     * 前提：select 提交值不在 options 内。
     * 期望：抛 TF_INPUT_INVALID。
     */
    @Test
    @Order(3)
    @DisplayName("select 值须落在 options")
    void validateAndApply_selectOutOfOptions() {
        Map<String, Object> field = new HashMap<>();
        field.put("name", "channel");
        field.put("type", "select");
        field.put("required", true);
        field.put("options", List.of(Map.of("label", "A", "value", "admin")));
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> InputFieldTypes.validateAndApply(
                        List.of(field), Map.of("channel", "x"), new HashMap<>()));
        assertEquals(FlowErrorCode.TF_INPUT_INVALID, ex.getErrorCode());
    }

    /**
     * 前提：password 字段提交明文。
     * 期望：flow 存明文；assigns.after 为 ***。
     */
    @Test
    @Order(4)
    @DisplayName("password assigns 脱敏")
    void validateAndApply_passwordMaskedInAssigns() {
        List<Map<String, Object>> fields = List.of(
                Map.of("name", "secret", "type", "password", "required", true)
        );
        Map<String, Object> flow = new HashMap<>();
        List<Map<String, Object>> assigns = InputFieldTypes.validateAndApply(
                fields, Map.of("secret", "s3cret"), flow);
        assertEquals("s3cret", flow.get("secret"));
        assertEquals("***", assigns.get(0).get("after"));
    }
}
