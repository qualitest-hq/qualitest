package com.qualitest.project.support.templatepack;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.mapper.TestProjectTemplateMapper;
import com.qualitest.project.service.ITestProjectTemplateService;
import com.qualitest.project.support.templatepack.ProjectTemplateFullPackCodec.PackKind;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.ExpandResult;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.ImportRequest;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.PackOpResult;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.SlimTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 项目模板包导入 / 导出 / 校验。
 * <ul>
 *   <li>完整包：含 templateApis、templateFlows 等，可往返导入导出</li>
 *   <li>精简包：仅 apis / assets / env 等短字段，展开后入库且不生成测试流</li>
 * </ul>
 */
@Service
public class ProjectTemplatePackService {

    private final ProjectTemplateSlimExpander expander;
    private final ProjectTemplateFullPackCodec fullPackCodec;
    private final ITestProjectTemplateService templateService;
    private final TestProjectTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    public ProjectTemplatePackService(
            ProjectTemplateSlimExpander expander,
            ProjectTemplateFullPackCodec fullPackCodec,
            ITestProjectTemplateService templateService,
            TestProjectTemplateMapper templateMapper,
            ObjectMapper objectMapper) {
        this.expander = expander;
        this.fullPackCodec = fullPackCodec;
        this.templateService = templateService;
        this.templateMapper = templateMapper;
        this.objectMapper = objectMapper;
    }

    /** 返回精简包 JSON Schema 原文，供外部校验结构。 */
    public String loadSchemaJson() {
        return loadClasspathUtf8("project-template/project-template.schema.json", "精简 Schema");
    }

    /**
     * 返回可复制给 AI 的精简包生成提示词正文。
     * 会去掉资源文件开头说明，只保留分隔线之后的可粘贴内容。
     */
    public String loadAiPromptText() {
        return extractCopyablePrompt(loadClasspathUtf8("project-template/AI_PROMPT.md", "精简生成提示词"));
    }

    /** 按路径读取 classpath UTF-8 文本；失败抛业务异常。 */
    private static String loadClasspathUtf8(String path, String label) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return IoUtil.read(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServiceException("无法读取" + label + ": " + e.getMessage());
        }
    }

    /**
     * 从提示词资源全文中取出可复制正文：
     * 若存在独立一行的 --- 分隔线，取其后内容；否则取全文。
     */
    static String extractCopyablePrompt(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new ServiceException("精简生成提示词为空");
        }
        String normalized = raw.replace("\r\n", "\n");
        int sep = normalized.indexOf("\n---\n");
        String body = sep >= 0 ? normalized.substring(sep + "\n---\n".length()) : normalized;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            throw new ServiceException("精简生成提示词正文为空");
        }
        return trimmed;
    }

    /** 校验完整包或精简包：展开预览，不写库。 */
    public PackOpResult validate(JsonNode template) {
        Resolved resolved = resolve(template);
        return toOpResult(null, resolved);
    }

    /**
     * 导入为自定义模板（builtinStatus=0）。
     * dryRun=true 只预览不写库；overwriteByName=true 时同名自定义模板做更新。
     * 写库走 insertTemplatePack / updateTemplatePack，因此完整包里的 templateFlows 可以落库；
     * 精简包展开后 flows 本就为空，不受影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public PackOpResult importTemplate(ImportRequest request) {
        if (request == null || request.getTemplate() == null || request.getTemplate().isNull()) {
            throw new ServiceException("template 不能为空");
        }
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        boolean overwrite = Boolean.TRUE.equals(request.getOverwriteByName());
        Resolved resolved = resolve(request.getTemplate());
        TestProjectTemplate entity = resolved.entity;

        if (dryRun) {
            return toOpResult(null, resolved);
        }

        Long existingId = findCustomIdByName(entity.getTemplateName());
        if (existingId != null) {
            if (!overwrite) {
                throw new ServiceException("模板名称已存在: " + entity.getTemplateName() + "（可设 overwriteByName=true）");
            }
            entity.setTestProjectTemplateId(existingId);
            // 允许覆盖写预制流（完整包同名导入）
            templateService.updateTemplatePack(entity);
            return toOpResult(existingId, resolved);
        }
        // 允许写入预制流（完整包 / 另存）
        templateService.insertTemplatePack(entity);
        return toOpResult(entity.getTestProjectTemplateId(), resolved);
    }

    /**
     * 按模板 id 导出完整包 Map（含 formatVersion、接口、参数、环境、测试流、提示词等）。
     * 已删除或不存在的模板会失败。
     */
    public Map<String, Object> exportPack(Long testProjectTemplateId) {
        TestProjectTemplate entity = templateService.selectTestProjectTemplateById(testProjectTemplateId);
        if (entity == null || (entity.getDelStatus() != null && entity.getDelStatus() == 1)) {
            throw new ServiceException("模板不存在");
        }
        return fullPackCodec.toPack(entity);
    }

    /**
     * 识别 JSON 形态并解析为实体 + 摘要 + 预览。
     * 有 templateApis → 完整包；有 apis 数组 → 精简包展开。
     */
    private Resolved resolve(JsonNode template) {
        if (template == null || template.isNull()) {
            throw new ServiceException("template 不能为空");
        }
        PackKind kind = fullPackCodec.detectKind(template);
        if (kind == PackKind.FULL) {
            TestProjectTemplate entity = fullPackCodec.fromPack(template);
            return resolved("full", entity, List.of(), fullPackCodec.summaryOf(entity), fullPackCodec.previewOf(entity));
        }
        fullPackCodec.assertFormatVersion(template);
        SlimTemplate slim;
        try {
            slim = objectMapper.treeToValue(template, SlimTemplate.class);
        } catch (Exception e) {
            throw new ServiceException("精简模板 JSON 解析失败: " + e.getMessage());
        }
        ExpandResult expanded = expander.expand(slim);
        Map<String, Object> summary = expanded.getExpandedSummary();
        if (summary != null) {
            summary.put("kind", "slim");
        }
        List<String> warnings = expanded.getWarnings() == null ? List.of() : List.copyOf(expanded.getWarnings());
        return resolved("slim", expanded.getEntity(), warnings, summary, expanded.getPreview());
    }

    /** 组装解析中间结果。 */
    private static Resolved resolved(
            String kind,
            TestProjectTemplate entity,
            List<String> warnings,
            Map<String, Object> expandedSummary,
            Map<String, Object> preview) {
        Resolved resolved = new Resolved();
        resolved.kind = kind;
        resolved.entity = entity;
        resolved.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        resolved.expandedSummary = expandedSummary;
        resolved.preview = preview;
        return resolved;
    }

    /**
     * 按名称查找未删除的自定义模板 id（跳过内置）。
     * 用于同名覆盖导入。
     */
    private Long findCustomIdByName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        String trimmed = name.trim();
        TestProjectTemplate probe = new TestProjectTemplate();
        probe.setTemplateName(trimmed);
        probe.setDelStatus(0);
        List<TestProjectTemplate> list = templateMapper.selectTestProjectTemplateList(probe);
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (TestProjectTemplate row : list) {
            if (row == null || (row.getBuiltinStatus() != null && row.getBuiltinStatus() == 1)) {
                continue;
            }
            if (trimmed.equals(row.getTemplateName())) {
                return row.getTestProjectTemplateId();
            }
        }
        return null;
    }

    /** 中间结果转为接口响应。 */
    private static PackOpResult toOpResult(Long id, Resolved resolved) {
        PackOpResult result = new PackOpResult();
        result.setTestProjectTemplateId(id);
        result.setKind(resolved.kind);
        result.setWarnings(resolved.warnings);
        result.setExpandedSummary(resolved.expandedSummary);
        result.setPreview(resolved.preview);
        return result;
    }

    /** validate / import 共用的解析中间态。 */
    private static final class Resolved {
        /** full 或 slim */
        private String kind;
        private TestProjectTemplate entity;
        private List<String> warnings;
        private Map<String, Object> expandedSummary;
        private Map<String, Object> preview;
    }
}
