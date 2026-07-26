package com.qualitest.ai.scenario.apidesign;

import com.qualitest.ai.scenario.apidesign.model.ApiDesignPatch;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignPatchChange;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将模型提交的 ApiDesignPatch 规范化并校验。
 * <p>
 * 按 target 分发：脚本大小与违禁词、约束键合法性、测值不得夹带约束、meta 字段白名单。
 * 同 unitId 后者覆盖前者；通过后的 patch 才可交给前端展示。
 */
@Component
public class ApiDesignPatchNormalizer {

    /** 单段脚本最大字节数 */
    private static final int MAX_SCRIPT_BYTES = 32 * 1024;
    /** 接口名称/说明最大字符数 */
    private static final int MAX_META_CHARS = 2000;

    /** 扁平参数约束落点 */
    private static final Set<String> FLAT_PARAM_TARGETS = Set.of(
            "request.queryParams",
            "request.pathParams",
            "request.declaredHeaders",
            "request.body.formData",
            "request.body.urlencoded"
    );

    /** JSON Schema 约束落点 */
    private static final Set<String> SCHEMA_TARGETS = Set.of(
            "request.body.schema",
            "response.schema"
    );

    /** 测值落点 */
    private static final Set<String> TEST_VALUE_TARGETS = Set.of(
            "testValue.request.paramDefaults",
            "testValue.request.bodyExample",
            "testValue.response.examplesById"
    );

    /** 小写 target → 规范写法（容忍大小写混用） */
    private static final Map<String, String> TARGET_ALIASES = Map.ofEntries(
            Map.entry("request.queryparams", "request.queryParams"),
            Map.entry("request.pathparams", "request.pathParams"),
            Map.entry("request.declaredheaders", "request.declaredHeaders"),
            Map.entry("request.body.formdata", "request.body.formData"),
            Map.entry("request.body.urlencoded", "request.body.urlencoded"),
            Map.entry("request.body.schema", "request.body.schema"),
            Map.entry("response.schema", "response.schema"),
            Map.entry("testvalue.request.paramdefaults", "testValue.request.paramDefaults"),
            Map.entry("testvalue.request.bodyexample", "testValue.request.bodyExample"),
            Map.entry("testvalue.response.examplesbyid", "testValue.response.examplesById"),
            Map.entry("script", "script"),
            Map.entry("meta", "meta")
    );

    /** 规范化结果：可用的 patch + 校验结论 */
    public record NormalizeResult(ApiDesignPatch patch, ApiDesignValidationResult validation) {}

    /**
     * 入口：要求 summary 非空、changes 非空；逐条规范化后按 unitId 去重。
     */
    public NormalizeResult normalize(ApiDesignPatch raw) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        ApiDesignPatch patch = new ApiDesignPatch();
        if (raw == null) {
            errors.add("patch 为空");
            return new NormalizeResult(patch, buildResult(false, errors, warnings));
        }
        patch.setSummary(trimOrNull(raw.getSummary()));
        if (patch.getSummary() == null) {
            errors.add("缺少 summary");
        }
        List<ApiDesignPatchChange> changes = raw.getChanges();
        if (changes == null || changes.isEmpty()) {
            errors.add("changes 不能为空");
            return new NormalizeResult(patch, buildResult(false, errors, warnings));
        }

        Map<String, ApiDesignPatchChange> byUnit = new LinkedHashMap<>();
        for (ApiDesignPatchChange change : changes) {
            if (change == null) {
                continue;
            }
            String target = canonicalizeTarget(change.getTarget());
            if (target == null) {
                errors.add("无效的 target: " + change.getTarget());
                continue;
            }
            ApiDesignPatchChange normalized = null;
            if ("script".equals(target)) {
                normalized = normalizeScript(change, errors);
            } else if (FLAT_PARAM_TARGETS.contains(target) || SCHEMA_TARGETS.contains(target)) {
                normalized = normalizeConstraint(change, target, errors, warnings);
            } else if (TEST_VALUE_TARGETS.contains(target)) {
                normalized = normalizeTestValue(change, target, errors);
            } else if ("meta".equals(target)) {
                normalized = normalizeMeta(change, errors);
            } else {
                errors.add("不支持的 target: " + target);
            }
            if (normalized == null) {
                continue;
            }
            String unitId = normalized.getUnitId();
            if (byUnit.containsKey(unitId)) {
                warnings.add("同一轮对 " + unitId + " 有多条变更，后者覆盖前者");
            }
            byUnit.put(unitId, normalized);
        }
        patch.setChanges(new ArrayList<>(byUnit.values()));
        if (patch.getChanges().isEmpty() && errors.isEmpty()) {
            errors.add("无有效 changes");
        }
        boolean ok = errors.isEmpty() && !patch.getChanges().isEmpty();
        return new NormalizeResult(patch, buildResult(ok, errors, warnings));
    }

    /** 规范化脚本变更：phase、长度上限、禁止 import/require/eval */
    private ApiDesignPatchChange normalizeScript(ApiDesignPatchChange change, List<String> errors) {
        String phase = normalizePhase(change.getPhase());
        if (phase == null) {
            errors.add("script 变更缺少 phase，须为 pre 或 post");
            return null;
        }
        String action = normalizeAction(change.getAction());
        if (action == null || (!"update".equals(action) && !"clear".equals(action))) {
            errors.add("script 的 action 须为 update 或 clear");
            return null;
        }
        String unitId = trimOrNull(change.getUnitId());
        if (unitId == null) {
            unitId = "script:" + phase;
        }
        ApiDesignPatchChange normalized = new ApiDesignPatchChange();
        normalized.setUnitId(unitId);
        normalized.setTarget("script");
        normalized.setPhase(phase);
        normalized.setAction(action);
        if ("update".equals(action)) {
            String content = change.getContent();
            if (content == null || content.isBlank()) {
                errors.add(unitId + " 脚本 update 缺少 content");
                return null;
            }
            if (content.getBytes().length > MAX_SCRIPT_BYTES) {
                errors.add(unitId + " 脚本超过 " + MAX_SCRIPT_BYTES + " 字节上限");
                return null;
            }
            if (containsForbiddenPattern(content)) {
                errors.add(unitId + " 脚本含禁止的 import/require/eval");
                return null;
            }
            normalized.setContent(content);
        }
        return normalized;
    }

    /** 规范化约束变更：必须 updateConstraints；按扁平/schema 裁剪约束键 */
    private ApiDesignPatchChange normalizeConstraint(ApiDesignPatchChange change,
                                                     String target,
                                                     List<String> errors,
                                                     List<String> warnings) {
        String action = normalizeAction(change.getAction());
        if (!"updateConstraints".equals(action)) {
            errors.add(target + " 的 action 须为 updateConstraints");
            return null;
        }
        String path = trimOrNull(change.getPath());
        if (path == null) {
            errors.add(target + " 缺少 path");
            return null;
        }
        boolean flat = FLAT_PARAM_TARGETS.contains(target);
        String type = ApiFieldTypeConstraintSupport.normalizeType(change.getType());
        if (type.isEmpty()) {
            warnings.add(target + "." + path + " 未提供 type，约束按宽松校验");
        }
        Map<String, Object> rawConstraints = change.getConstraints();
        if (rawConstraints == null || rawConstraints.isEmpty()) {
            // 允许仅更新 description
            if (trimOrNull(change.getDescription()) == null) {
                errors.add(target + "." + path + " 缺少 constraints 或 description");
                return null;
            }
        }
        ApiFieldTypeConstraintSupport.PruneResult pruned =
                ApiFieldTypeConstraintSupport.pruneConstraints(rawConstraints, type, flat);
        errors.addAll(pruned.errors());
        warnings.addAll(pruned.warnings());
        if (pruned.hasErrors()) {
            return null;
        }
        if (pruned.constraints().isEmpty() && trimOrNull(change.getDescription()) == null) {
            errors.add(target + "." + path + " prune 后无有效约束");
            return null;
        }

        String responseId = trimOrNull(change.getResponseId());
        String unitId = trimOrNull(change.getUnitId());
        if (unitId == null) {
            if ("response.schema".equals(target) && responseId != null) {
                unitId = "constraint:" + target + "." + responseId + "." + path;
            } else {
                unitId = "constraint:" + target + "." + path;
            }
        }

        ApiDesignPatchChange normalized = new ApiDesignPatchChange();
        normalized.setUnitId(unitId);
        normalized.setTarget(target);
        normalized.setAction("updateConstraints");
        normalized.setPath(path);
        if (!type.isEmpty()) {
            normalized.setType(type);
        }
        if (responseId != null) {
            normalized.setResponseId(responseId);
        }
        normalized.setConstraints(new LinkedHashMap<>(pruned.constraints()));
        String desc = trimOrNull(change.getDescription());
        if (desc != null) {
            normalized.setDescription(desc);
        }
        return normalized;
    }

    /** 规范化测值变更：set/clear；不得附带 constraints */
    private ApiDesignPatchChange normalizeTestValue(ApiDesignPatchChange change,
                                                    String target,
                                                    List<String> errors) {
        String action = normalizeAction(change.getAction());
        if (!"set".equals(action) && !"clear".equals(action)) {
            errors.add(target + " 的 action 须为 set 或 clear");
            return null;
        }
        // 测值不得夹带约束键
        if (change.getConstraints() != null && !change.getConstraints().isEmpty()) {
            errors.add(target + " 测值变更不得包含 constraints");
            return null;
        }
        String path = trimOrNull(change.getPath());
        if ("testValue.request.paramDefaults".equals(target)
                || "testValue.response.examplesById".equals(target)) {
            if (path == null) {
                errors.add(target + " 缺少 path");
                return null;
            }
        }
        if ("set".equals(action) && change.getValue() == null) {
            errors.add(target + " set 缺少 value");
            return null;
        }

        String unitId = trimOrNull(change.getUnitId());
        if (unitId == null) {
            if (path != null) {
                unitId = "testValue:" + target.substring("testValue.".length()) + "." + path;
            } else {
                unitId = "testValue:" + target.substring("testValue.".length());
            }
        }

        ApiDesignPatchChange normalized = new ApiDesignPatchChange();
        normalized.setUnitId(unitId);
        normalized.setTarget(target);
        normalized.setAction(action);
        if (path != null) {
            normalized.setPath(path);
        }
        if ("set".equals(action)) {
            normalized.setValue(change.getValue());
        }
        return normalized;
    }

    /** 规范化基本信息变更：仅 apiDescription / apiName */
    private ApiDesignPatchChange normalizeMeta(ApiDesignPatchChange change, List<String> errors) {
        String action = normalizeAction(change.getAction());
        if (!"update".equals(action) && !"clear".equals(action)) {
            errors.add("meta 的 action 须为 update 或 clear");
            return null;
        }
        String path = trimOrNull(change.getPath());
        if (path == null) {
            path = "apiDescription";
        }
        if (!"apiDescription".equals(path) && !"apiName".equals(path)) {
            errors.add("meta.path 仅支持 apiDescription 或 apiName");
            return null;
        }
        String unitId = trimOrNull(change.getUnitId());
        if (unitId == null) {
            unitId = "meta:" + path;
        }
        ApiDesignPatchChange normalized = new ApiDesignPatchChange();
        normalized.setUnitId(unitId);
        normalized.setTarget("meta");
        normalized.setPath(path);
        normalized.setAction(action);
        if ("update".equals(action)) {
            String content = change.getContent();
            if (content == null && change.getValue() != null) {
                content = String.valueOf(change.getValue());
            }
            if (content == null) {
                errors.add(unitId + " update 缺少 content");
                return null;
            }
            if (content.length() > MAX_META_CHARS) {
                errors.add(unitId + " 超过 " + MAX_META_CHARS + " 字符上限");
                return null;
            }
            normalized.setContent(content);
        }
        return normalized;
    }

    /** 将 target 规范化为白名单写法，无法识别时返回 null。 */
    private static String canonicalizeTarget(String target) {
        if (target == null) {
            return null;
        }
        String trimmed = target.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return TARGET_ALIASES.get(lower);
    }

    /** 检测脚本是否含 import、require 或 eval。 */
    private static boolean containsForbiddenPattern(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("import ") || lower.contains("require(") || lower.contains("eval(");
    }

    /** 规范化脚本 phase，仅接受 pre 或 post。 */
    private static String normalizePhase(String phase) {
        if (phase == null) {
            return null;
        }
        String p = phase.trim().toLowerCase(Locale.ROOT);
        if ("pre".equals(p) || "post".equals(p)) {
            return p;
        }
        return null;
    }

    /** 规范化 action 字符串，兼容大小写混写。 */
    private static String normalizeAction(String action) {
        if (action == null) {
            return null;
        }
        String a = action.trim();
        if (a.isEmpty()) {
            return null;
        }
        String lower = a.toLowerCase(Locale.ROOT);
        if ("update".equals(lower) || "clear".equals(lower) || "set".equals(lower)) {
            return lower;
        }
        if ("updateconstraints".equals(lower)) {
            return "updateConstraints";
        }
        return a;
    }

    /** 去首尾空白，空串视为 null。 */
    private static String trimOrNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 组装校验结果对象。 */
    private static ApiDesignValidationResult buildResult(boolean ok, List<String> errors, List<String> warnings) {
        return ApiDesignValidationResult.builder()
                .ok(ok)
                .errors(new ArrayList<>(errors))
                .warnings(new ArrayList<>(warnings))
                .build();
    }
}
