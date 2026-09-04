package com.qualitest.project.support.templatepack;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目模板完整包编解码。
 * 完整包字段对应库表各 JSON 列：matchConfig、templateApis、templateParams、
 * templateEnvs、templateFlows、templatePrompts；导出时数组已解析为对象，导入时再写成 JSON 字符串。
 */
@Component
public class ProjectTemplateFullPackCodec {

    /** 本服务支持的最高完整包版本号；更大则拒绝导入。 */
    public static final int CURRENT_FORMAT_VERSION = 1;

    /** 导入 JSON 形态。 */
    public enum PackKind {
        /** 含 templateApis 的完整包 */
        FULL,
        /** 含 apis 的精简冷启动包 */
        SLIM
    }

    /**
     * 判断 JSON 是完整包还是精简包。
     * 同时带 templateApis 时优先按完整包处理。
     */
    public PackKind detectKind(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new ServiceException("template 必须是 JSON 对象");
        }
        if (node.has("templateApis") && !node.get("templateApis").isNull()) {
            return PackKind.FULL;
        }
        if (node.has("apis") && node.get("apis").isArray()) {
            return PackKind.SLIM;
        }
        throw new ServiceException("无法识别模板格式：完整包需 templateApis，精简包需 apis");
    }

    /**
     * 校验 formatVersion。
     * 缺省或 null 视为可接受；非数字、小于 1、或大于当前最高版本则失败。
     */
    public void assertFormatVersion(JsonNode node) {
        if (node == null || !node.has("formatVersion") || node.get("formatVersion").isNull()) {
            return;
        }
        JsonNode v = node.get("formatVersion");
        if (!v.isNumber()) {
            throw new ServiceException("formatVersion 必须是数字");
        }
        int ver = v.asInt();
        if (ver < 1) {
            throw new ServiceException("formatVersion 无效: " + ver);
        }
        if (ver > CURRENT_FORMAT_VERSION) {
            throw new ServiceException("formatVersion=" + ver + " 不受支持，当前最高为 " + CURRENT_FORMAT_VERSION);
        }
    }

    /**
     * 实体 → 完整包 Map，供导出下载。
     * templateFlows 等列从 JSON 字符串解析成数组；空列给出空数组。
     */
    public Map<String, Object> toPack(TestProjectTemplate entity) {
        Map<String, Object> pack = new LinkedHashMap<>();
        if (entity == null) {
            return pack;
        }
        pack.put("formatVersion", CURRENT_FORMAT_VERSION);
        pack.put("templateName", entity.getTemplateName());
        if (StrUtil.isNotBlank(entity.getRemark())) {
            pack.put("remark", entity.getRemark());
        }
        if (entity.getEnableStatus() != null) {
            pack.put("enableStatus", entity.getEnableStatus());
        }
        if (entity.getSortNum() != null) {
            pack.put("sortNum", entity.getSortNum());
        }
        pack.put("matchConfig", parseMatchConfig(entity.getMatchConfig()));
        pack.put("templateApis", parseArrayOrEmpty(entity.getTemplateApis()));
        pack.put("templateParams", parseArrayOrEmpty(entity.getTemplateParams()));
        pack.put("templateEnvs", parseArrayOrEmpty(entity.getTemplateEnvs()));
        pack.put("templateFlows", parseArrayOrEmpty(entity.getTemplateFlows()));
        pack.put("templatePrompts", parseArrayOrEmpty(entity.getTemplatePrompts()));
        return pack;
    }

    /**
     * 完整包 JSON → 待入库实体。
     * 固定写成自定义模板；templateFlows 允许空数组；templateApis 不能缺或空。
     */
    public TestProjectTemplate fromPack(JsonNode node) {
        assertFormatVersion(node);
        String templateName = textOrNull(node.get("templateName"));
        if (templateName == null) {
            throw new ServiceException("templateName 不能为空");
        }
        JsonNode apisNode = node.get("templateApis");
        if (apisNode == null || apisNode.isNull()) {
            throw new ServiceException("templateApis 不能为空");
        }
        String templateApis = toJsonArrayString(apisNode, "templateApis");
        if ("[]".equals(templateApis.trim())) {
            throw new ServiceException("templateApis 不能为空数组");
        }

        TestProjectTemplate entity = TestProjectTemplate.builder()
                .templateName(templateName)
                .matchConfig(normalizeMatchConfig(node.get("matchConfig")))
                .templateApis(templateApis)
                .templateParams(toJsonArrayString(node.get("templateParams"), "templateParams"))
                .templateEnvs(toJsonArrayString(node.get("templateEnvs"), "templateEnvs"))
                .templateFlows(toJsonArrayString(node.get("templateFlows"), "templateFlows"))
                .templatePrompts(toJsonArrayString(node.get("templatePrompts"), "templatePrompts"))
                .builtinStatus(0)
                .enableStatus(intOrDefault(node.get("enableStatus"), 1))
                .sortNum(intOrDefault(node.get("sortNum"), 0))
                .delStatus(0)
                .build();
        entity.setRemark(textOrNull(node.get("remark")));
        return entity;
    }

    /** 统计接口 / 素材 / 环境 / 流 / 提示词数量，并标记 kind=full。 */
    public Map<String, Object> summaryOf(TestProjectTemplate entity) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("kind", "full");
        summary.put("apiCount", arraySize(entity.getTemplateApis()));
        summary.put("assetCount", countAssetParams(entity.getTemplateParams()));
        summary.put("envCount", arraySize(entity.getTemplateEnvs()));
        summary.put("flowCount", arraySize(entity.getTemplateFlows()));
        summary.put("promptCount", arraySize(entity.getTemplatePrompts()));
        return summary;
    }

    /**
     * 导入预览用字段快照：完整包内容去掉 formatVersion。
     */
    public Map<String, Object> previewOf(TestProjectTemplate entity) {
        Map<String, Object> preview = toPack(entity);
        preview.remove("formatVersion");
        return preview;
    }

    /** 库内 matchConfig 字符串 → 导出用对象；解析失败则原样返回字符串。 */
    private static Object parseMatchConfig(String raw) {
        if (StrUtil.isBlank(raw) || "null".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return JSONUtil.parse(raw.trim());
        } catch (Exception e) {
            return raw;
        }
    }

    /** 导入 matchConfig：对象/数组转 JSON 字符串，空对象当 null。 */
    private static String normalizeMatchConfig(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String s = StrUtil.trimToNull(node.asText());
            if (s == null || "null".equalsIgnoreCase(s)) {
                return null;
            }
            return s;
        }
        if (node.isObject() || node.isArray()) {
            String s = node.toString();
            return "{}".equals(s) ? null : s;
        }
        return null;
    }

    /** 库内 JSON 数组字符串 → List；坏数据当空列表。 */
    private static List<Object> parseArrayOrEmpty(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            JSONArray arr = JSONUtil.parseArray(raw);
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                out.add(arr.get(i));
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 导入字段 → 入库用 JSON 数组字符串。
     * 已是数组则序列化；已是合法数组文本则沿用；缺省为 []。
     */
    private static String toJsonArrayString(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return "[]";
        }
        if (node.isTextual()) {
            String s = node.asText().trim();
            if (s.isEmpty()) {
                return "[]";
            }
            try {
                JSONUtil.parseArray(s);
                return s;
            } catch (Exception e) {
                throw new ServiceException(field + " 必须是 JSON 数组");
            }
        }
        if (!node.isArray()) {
            throw new ServiceException(field + " 必须是 JSON 数组");
        }
        return node.toString();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return StrUtil.trimToNull(node.asText());
    }

    private static int intOrDefault(JsonNode node, int defaultValue) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return defaultValue;
        }
        return node.asInt();
    }

    private static int arraySize(String raw) {
        return parseArrayOrEmpty(raw).size();
    }

    /** 统计 templateParams 里 kind=asset 的条数。 */
    private static int countAssetParams(String templateParams) {
        int n = 0;
        for (Object row : parseArrayOrEmpty(templateParams)) {
            if (row == null) {
                continue;
            }
            try {
                if ("asset".equals(JSONUtil.parseObj(row).getStr("kind"))) {
                    n++;
                }
            } catch (Exception ignored) {
                // 单行解析失败则跳过
            }
        }
        return n;
    }
}
