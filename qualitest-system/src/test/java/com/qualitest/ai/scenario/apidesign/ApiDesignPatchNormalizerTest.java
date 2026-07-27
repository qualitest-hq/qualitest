package com.qualitest.ai.scenario.apidesign;

import com.qualitest.ai.scenario.apidesign.model.ApiDesignPatch;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignPatchChange;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * 测 ApiDesignPatchNormalizer：API 设计 patch 校验与归一（脚本 / 约束 / 禁止 import）。
 * 边界：纯函数 Normalizer，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiDesignPatchNormalizerTest
 */
class ApiDesignPatchNormalizerTest {

    private final ApiDesignPatchNormalizer normalizer = new ApiDesignPatchNormalizer();

    /**
     * 前提：合法 pre 脚本 update。
     * 期望：校验通过；unitId 归一为 script:pre。
     */
    @Test
    void normalize_validScriptUpdate_ok() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("生成签名脚本");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("script");
        change.setPhase("pre");
        change.setAction("update");
        change.setContent("api.variables.set('sign', api.hmacSha256('data', api.environment.get('secret')));");
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertTrue(result.validation().isOk());
        assertEquals(1, result.patch().getChanges().size());
        assertEquals("script:pre", result.patch().getChanges().get(0).getUnitId());
    }

    /**
     * 前提：post 脚本 clear，已带 unitId。
     * 期望：校验通过，action 仍为 clear。
     */
    @Test
    void normalize_clearPost_ok() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("清空后置");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setUnitId("script:post");
        change.setTarget("script");
        change.setPhase("post");
        change.setAction("clear");
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertTrue(result.validation().isOk());
        assertEquals("clear", result.patch().getChanges().get(0).getAction());
    }

    /**
     * 前提：脚本内容含 forbidden import。
     * 期望：校验失败。
     */
    @Test
    void normalize_forbiddenImport_fails() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("bad");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("script");
        change.setPhase("pre");
        change.setAction("update");
        change.setContent("import fs from 'fs';");
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertFalse(result.validation().isOk());
    }

    /**
     * 前提：updateConstraints 针对 request.queryParams.mobile。
     * 期望：校验通过（约束补丁合法）。
     */
    @Test
    void normalize_constraintUpdate_ok() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("补手机号 pattern");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("request.queryParams");
        change.setPath("mobile");
        change.setType("string");
        change.setAction("updateConstraints");
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("pattern", "^1\\d{10}$");
        constraints.put("maxLength", 11);
        change.setConstraints(constraints);
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertTrue(result.validation().isOk());
        assertEquals("constraint:request.queryParams.mobile", result.patch().getChanges().get(0).getUnitId());
        assertEquals("updateConstraints", result.patch().getChanges().get(0).getAction());
    }

    /**
     * 前提：约束含 enum。
     * 期望：校验失败，错误信息含 enum。
     */
    @Test
    void normalize_enumRejected() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("bad enum");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("request.queryParams");
        change.setPath("status");
        change.setType("string");
        change.setAction("updateConstraints");
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("enum", java.util.List.of("a", "b"));
        change.setConstraints(constraints);
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertFalse(result.validation().isOk());
        assertTrue(result.validation().getErrors().stream().anyMatch(e -> e.contains("enum")));
    }

    /**
     * 前提：integer 约束同时带 pattern 与 minValue。
     * 期望：通过但剥掉 pattern，保留 minValue，并有 warning。
     */
    @Test
    void normalize_integerWithPattern_strippedOrFails() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("type mismatch");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("request.queryParams");
        change.setPath("age");
        change.setType("integer");
        change.setAction("updateConstraints");
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("pattern", "^\\d+$");
        constraints.put("minValue", 1);
        change.setConstraints(constraints);
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertTrue(result.validation().isOk());
        assertFalse(result.patch().getChanges().get(0).getConstraints().containsKey("pattern"));
        assertTrue(result.patch().getChanges().get(0).getConstraints().containsKey("minValue"));
        assertFalse(result.validation().getWarnings().isEmpty());
    }

    /**
     * 前提：body.schema 约束误用 minValue（表单字段名）。
     * 期望：校验失败。
     */
    @Test
    void normalize_schemaRejectsMinValue() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("wrong key name");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("request.body.schema");
        change.setPath("user.age");
        change.setType("integer");
        change.setAction("updateConstraints");
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("minValue", 1);
        change.setConstraints(constraints);
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertFalse(result.validation().isOk());
    }

    /**
     * 前提：testValue.request.paramDefaults 的 set。
     * 期望：校验通过。
     */
    @Test
    void normalize_testValueSet_ok() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("默认测值");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("testValue.request.paramDefaults");
        change.setPath("mobile");
        change.setAction("set");
        change.setValue("{{asset.demoUser.mobile}}");
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertTrue(result.validation().isOk());
        assertEquals("set", result.patch().getChanges().get(0).getAction());
    }

    /**
     * 前提：testValue set 同时带 constraints。
     * 期望：校验失败（测值与约束不可混用）。
     */
    @Test
    void normalize_testValueWithConstraints_fails() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("bad mix");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("testValue.request.paramDefaults");
        change.setPath("mobile");
        change.setAction("set");
        change.setValue("1");
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("pattern", "x");
        change.setConstraints(constraints);
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertFalse(result.validation().isOk());
    }

    /**
     * 前提：meta.apiDescription update。
     * 期望：通过；unitId 为 meta:apiDescription。
     */
    @Test
    void normalize_metaDescription_ok() {
        ApiDesignPatch patch = new ApiDesignPatch();
        patch.setSummary("补说明");
        ApiDesignPatchChange change = new ApiDesignPatchChange();
        change.setTarget("meta");
        change.setPath("apiDescription");
        change.setAction("update");
        change.setContent("查询用户手机号");
        patch.getChanges().add(change);

        var result = normalizer.normalize(patch);
        assertTrue(result.validation().isOk());
        assertEquals("meta:apiDescription", result.patch().getChanges().get(0).getUnitId());
    }
}
