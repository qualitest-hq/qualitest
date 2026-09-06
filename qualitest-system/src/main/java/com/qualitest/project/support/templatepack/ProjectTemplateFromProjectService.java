package com.qualitest.project.support.templatepack;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitest.ai.domain.AiPromptTemplate;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.PrefabricatedApi;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.ApiAuthConfigSupport;
import com.qualitest.api.util.ApiImportMatchSupport;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.params.SaveAsAuthTemplateParams;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport;
import com.qualitest.project.support.TestProjectAssetSupport;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.ImportRequest;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.PackOpResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 从已跑通鉴权的测试项目打包完整项目模板。
 * 测试流为主轴：流内 HTTP 绑定接口与引用素材强制带上；可通过 extra* 追加 Profile 其余口、素材、环境、提示词。
 */
@Service
public class ProjectTemplateFromProjectService {

    private final ITestProjectService testProjectService;
    private final ITestProjectApiService testProjectApiService;
    private final ITestFlowService testFlowService;
    private final ITestProjectEnvService testProjectEnvService;
    private final IAiPromptTemplateService aiPromptTemplateService;
    private final TestProjectMapper testProjectMapper;
    private final ProjectTemplatePackService packService;
    private final ObjectMapper objectMapper;

    public ProjectTemplateFromProjectService(
            ITestProjectService testProjectService,
            ITestProjectApiService testProjectApiService,
            ITestFlowService testFlowService,
            ITestProjectEnvService testProjectEnvService,
            IAiPromptTemplateService aiPromptTemplateService,
            TestProjectMapper testProjectMapper,
            ProjectTemplatePackService packService,
            ObjectMapper objectMapper) {
        this.testProjectService = testProjectService;
        this.testProjectApiService = testProjectApiService;
        this.testFlowService = testFlowService;
        this.testProjectEnvService = testProjectEnvService;
        this.aiPromptTemplateService = aiPromptTemplateService;
        this.testProjectMapper = testProjectMapper;
        this.packService = packService;
        this.objectMapper = objectMapper;
    }

    /**
     * 按请求从项目组装完整包并导入为自定义模板。
     * dryRun 时不写库；流关联接口不可被 extra 剔除。
     */
    @Transactional(rollbackFor = Exception.class)
    public PackOpResult saveAsAuthTemplate(Long testProjectId, SaveAsAuthTemplateParams params) {
        if (testProjectId == null) {
            throw new ServiceException("请指定测试项目");
        }
        if (params == null || StrUtil.isBlank(params.getTemplateName())) {
            throw new ServiceException("templateName 不能为空");
        }
        if (StrUtil.isBlank(params.getProfileId())) {
            throw new ServiceException("profileId 不能为空");
        }

        TestProject project = testProjectService.selectTestProjectById(testProjectId);
        if (project == null || (project.getDelStatus() != null && project.getDelStatus() == 1)) {
            throw new ServiceException("测试项目不存在");
        }

        ProjectAuthConfig authConfig = ProjectAuthConfigSupport.parse(project.getAuthConfig());
        ProjectAuthProfile profile = findProfile(authConfig, params.getProfileId().trim());
        if (profile == null) {
            throw new ServiceException("未找到 Auth Profile: " + params.getProfileId());
        }

        List<String> warnings = new ArrayList<>();
        List<TestFlow> selectedFlows = loadSelectedFlows(testProjectId, params.getFlowIds(), warnings);
        Map<Long, TestProjectApi> apiById = loadProjectApis(testProjectId);

        Set<Long> lockedApiIds = new LinkedHashSet<>();
        for (TestFlow flow : selectedFlows) {
            for (String rawId : PrefabricatedTemplateExtrasSupport.collectGraphHttpApiIds(flow.getGraphJson())) {
                Long id = parseLongId(rawId);
                if (id != null && apiById.containsKey(id)) {
                    lockedApiIds.add(id);
                } else if (id != null) {
                    warnings.add("流「" + flow.getFlowName() + "」绑定的接口 id=" + rawId + " 在项目中不存在，已跳过");
                }
            }
        }
        if (!selectedFlows.isEmpty() && lockedApiIds.isEmpty()) {
            warnings.add("所选测试流未解析到有效项目接口绑定");
        }
        boolean anyExtract = selectedFlows.stream()
                .anyMatch(f -> PrefabricatedTemplateExtrasSupport.graphHasHttpExtracts(f.getGraphJson()));
        if (!selectedFlows.isEmpty() && !anyExtract) {
            warnings.add("所选测试流均无 HTTP extracts：再 Apply 时可能无法派生托管头");
        }
        if (selectedFlows.isEmpty()) {
            warnings.add("未选择测试流：模板将不含登录流");
        }

        Set<Long> allApiIds = new LinkedHashSet<>(lockedApiIds);
        if (params.getExtraApiIds() != null) {
            for (Long extraId : params.getExtraApiIds()) {
                if (extraId != null && apiById.containsKey(extraId)) {
                    allApiIds.add(extraId);
                }
            }
        }
        // Profile 预制口若无流/无 extra 且列表为空，至少尝试按 method+path 收进 Profile 口
        if (allApiIds.isEmpty() && profile.getApis() != null) {
            for (PrefabricatedApi prefab : profile.getApis()) {
                Long matched = findApiIdByPrefab(apiById, prefab);
                if (matched != null) {
                    allApiIds.add(matched);
                }
            }
        }
        if (allApiIds.isEmpty()) {
            throw new ServiceException("没有可打包的接口：请选择含 HTTP 绑定的测试流，或追加 Profile 鉴权口");
        }

        Map<String, String> projectIdStrToSynth = new LinkedHashMap<>();
        List<Map<String, Object>> templateApis = new ArrayList<>();
        for (Long apiId : allApiIds) {
            TestProjectApi api = apiById.get(apiId);
            if (api == null) {
                continue;
            }
            String synthId = String.valueOf(IdUtil.getSnowflakeNextId());
            projectIdStrToSynth.put(String.valueOf(apiId), synthId);
            templateApis.add(toPrefabApiMap(api, synthId));
        }

        List<Map<String, Object>> templateFlows = new ArrayList<>();
        for (TestFlow flow : selectedFlows) {
            String unbound = PrefabricatedTemplateExtrasSupport.unbindGraphApis(
                    flow.getGraphJson(), projectIdStrToSynth);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("flowName", flow.getFlowName());
            if (StrUtil.isNotBlank(flow.getFlowDescription())) {
                row.put("description", flow.getFlowDescription());
            }
            row.put("graphJson", parseGraphObject(unbound));
            templateFlows.add(row);
        }

        Set<String> lockedAssetKeys = new LinkedHashSet<>();
        for (TestFlow flow : selectedFlows) {
            PrefabricatedTemplateExtrasSupport.collectAssetKeys(flow.getGraphJson(), lockedAssetKeys);
        }
        for (Long apiId : allApiIds) {
            TestProjectApi api = apiById.get(apiId);
            if (api != null) {
                collectAssetKeysFromApi(api, lockedAssetKeys);
            }
        }
        PrefabricatedTemplateExtrasSupport.collectAssetKeys(profile.getHeaderValueTemplate(), lockedAssetKeys);

        Set<String> allAssetKeys = new LinkedHashSet<>(lockedAssetKeys);
        if (params.getExtraAssetKeys() != null) {
            for (String key : params.getExtraAssetKeys()) {
                if (StrUtil.isNotBlank(key)) {
                    allAssetKeys.add(key.trim());
                }
            }
        }

        Map<String, TestProjectAsset> assetsByKey = loadAssetsByKey(testProjectId);
        List<Map<String, Object>> templateParams = new ArrayList<>();
        for (String key : allAssetKeys) {
            TestProjectAsset entry = assetsByKey.get(key);
            if (entry == null) {
                warnings.add("素材 key「" + key + "」在项目中不存在，已跳过");
                continue;
            }
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("kind", "asset");
            param.put("name", key);
            param.put("value", entry.getAssets() != null ? entry.getAssets() : Map.of());
            if (StrUtil.isNotBlank(entry.getRemark())) {
                param.put("remark", entry.getRemark());
            }
            templateParams.add(param);
        }

        List<Map<String, Object>> templateEnvs = new ArrayList<>();
        boolean includeEnv = params.getIncludeEnv() == null || Boolean.TRUE.equals(params.getIncludeEnv());
        if (includeEnv) {
            TestProjectEnv first = firstEnv(testProjectId);
            if (first != null) {
                Map<String, Object> env = new LinkedHashMap<>();
                env.put("envName", StrUtil.blankToDefault(first.getEnvName(), "默认环境"));
                env.put("envUrl", StrUtil.blankToDefault(first.getEnvUrl(), ""));
                env.put("envVariables", parseEnvVariables(first.getEnvVariables()));
                templateEnvs.add(env);
            } else {
                warnings.add("项目无环境，未写入 templateEnvs");
            }
        }

        List<Map<String, Object>> templatePrompts = new ArrayList<>();
        if (params.getPromptIds() != null) {
            for (Long promptId : params.getPromptIds()) {
                if (promptId == null) {
                    continue;
                }
                AiPromptTemplate prompt = aiPromptTemplateService.selectAiPromptTemplateById(promptId);
                if (prompt == null
                        || (prompt.getDelStatus() != null && prompt.getDelStatus() == 1)
                        || !Objects.equals(testProjectId, prompt.getTestProjectId())) {
                    warnings.add("提示词 id=" + promptId + " 不属于本项目或不存在，已跳过");
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", prompt.getTemplateTitle());
                row.put("content", prompt.getTemplateContent());
                if (StrUtil.isNotBlank(prompt.getTemplateDescription())) {
                    row.put("description", prompt.getTemplateDescription());
                }
                row.put("sessionScene", StrUtil.blankToDefault(prompt.getSessionScene(), "test_flow_design"));
                row.put("sortNum", prompt.getSortNum() != null ? prompt.getSortNum() : 0);
                if (StrUtil.isNotBlank(prompt.getRemark())) {
                    row.put("remark", prompt.getRemark());
                }
                templatePrompts.add(row);
            }
        }

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("formatVersion", ProjectTemplateFullPackCodec.CURRENT_FORMAT_VERSION);
        pack.put("templateName", params.getTemplateName().trim());
        pack.put("enableStatus", 1);
        pack.put("sortNum", 0);
        pack.put("matchConfig", matchConfigOf(profile.getMatch()));
        pack.put("templateApis", templateApis);
        pack.put("templateParams", templateParams);
        pack.put("templateEnvs", templateEnvs);
        pack.put("templateFlows", templateFlows);
        pack.put("templatePrompts", templatePrompts);

        Map<String, Object> lockedSummary = new LinkedHashMap<>();
        lockedSummary.put("apiIds", lockedApiIds.stream().map(String::valueOf).toList());
        lockedSummary.put("assetKeys", new ArrayList<>(lockedAssetKeys));

        ImportRequest importRequest = new ImportRequest();
        importRequest.setDryRun(Boolean.TRUE.equals(params.getDryRun()));
        importRequest.setOverwriteByName(Boolean.TRUE.equals(params.getOverwriteByName()));
        try {
            importRequest.setTemplate(objectMapper.valueToTree(pack));
        } catch (Exception e) {
            throw new ServiceException("组装完整包失败: " + e.getMessage());
        }

        PackOpResult result = packService.importTemplate(importRequest);
        List<String> merged = new ArrayList<>();
        if (result.getWarnings() != null) {
            merged.addAll(result.getWarnings());
        }
        merged.addAll(warnings);
        result.setWarnings(merged);
        Map<String, Object> summary = result.getExpandedSummary();
        if (summary == null) {
            summary = new LinkedHashMap<>();
            result.setExpandedSummary(summary);
        }
        summary.put("locked", lockedSummary);
        summary.put("sourceTestProjectId", String.valueOf(testProjectId));
        summary.put("sourceProfileId", profile.getId());
        return result;
    }

    private static ProjectAuthProfile findProfile(ProjectAuthConfig config, String profileId) {
        if (config == null || config.getAuthProfiles() == null) {
            return null;
        }
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile != null && profileId.equals(profile.getId())) {
                return profile;
            }
        }
        return null;
    }

    private List<TestFlow> loadSelectedFlows(Long testProjectId, List<Long> flowIds, List<String> warnings) {
        List<TestFlow> out = new ArrayList<>();
        if (flowIds == null || flowIds.isEmpty()) {
            return out;
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long flowId : flowIds) {
            if (flowId == null || !seen.add(flowId)) {
                continue;
            }
            TestFlow flow = testFlowService.selectTestFlowById(flowId);
            if (flow == null
                    || (flow.getDelStatus() != null && flow.getDelStatus() == 1)
                    || !Objects.equals(testProjectId, flow.getTestProjectId())) {
                warnings.add("测试流 id=" + flowId + " 不属于本项目或不存在，已跳过");
                continue;
            }
            out.add(flow);
        }
        return out;
    }

    private Map<Long, TestProjectApi> loadProjectApis(Long testProjectId) {
        Map<Long, TestProjectApi> map = new LinkedHashMap<>();
        List<TestProjectApi> list = testProjectApiService.selectTestProjectApiList(
                TestProjectApi.builder().testProjectId(testProjectId).delStatus(0).build());
        if (list == null) {
            return map;
        }
        for (TestProjectApi api : list) {
            if (api != null && api.getTestProjectApiId() != null) {
                map.put(api.getTestProjectApiId(), api);
            }
        }
        return map;
    }

    private static Long findApiIdByPrefab(Map<Long, TestProjectApi> apiById, PrefabricatedApi prefab) {
        if (prefab == null || StrUtil.isBlank(prefab.getApiPath())) {
            return null;
        }
        String method = ProjectAuthConfigSupport.prefabricatedHttpMethod(prefab);
        String path = ProjectAuthConfigSupport.normalizeApiPath(prefab.getApiPath());
        String identity = method + " " + path;
        for (TestProjectApi api : apiById.values()) {
            if (identity.equals(ApiImportMatchSupport.buildIdentity(api))) {
                return api.getTestProjectApiId();
            }
        }
        return null;
    }

    private Map<String, TestProjectAsset> loadAssetsByKey(Long testProjectId) {
        Map<String, TestProjectAsset> map = new LinkedHashMap<>();
        String json = testProjectMapper.selectAssetVariablesByTestProjectId(testProjectId);
        for (TestProjectAsset entry : TestProjectAssetSupport.parseEntries(json)) {
            if (entry != null && StrUtil.isNotBlank(entry.getKey())) {
                map.put(entry.getKey().trim(), entry);
            }
        }
        return map;
    }

    private TestProjectEnv firstEnv(Long testProjectId) {
        List<TestProjectEnv> envs = testProjectEnvService.selectTestProjectEnvList(
                TestProjectEnv.builder().testProjectId(testProjectId).delStatus(0).build());
        if (envs == null || envs.isEmpty()) {
            return null;
        }
        return envs.get(0);
    }

    private static Map<String, Object> toPrefabApiMap(TestProjectApi api, String synthId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("testProjectApiId", synthId);
        row.put("apiName", StrUtil.blankToDefault(api.getApiName(), api.getApiPath()));
        row.put("apiPath", ProjectAuthConfigSupport.normalizeApiPath(api.getApiPath()));
        row.put("apiGroup", StrUtil.blankToDefault(api.getApiGroup(), "默认分组"));
        row.put("protocolType", StrUtil.blankToDefault(api.getProtocolType(), "http"));
        row.put("apiStatus", StrUtil.blankToDefault(api.getApiStatus(), "normal"));
        if (StrUtil.isNotBlank(api.getApiDescription())) {
            row.put("apiDescription", api.getApiDescription());
        }
        row.put("requestConfig", parseJsonOrRaw(api.getRequestConfig()));
        row.put("headers", parseJsonOrRaw(api.getHeaders()));
        row.put("cookies", parseJsonOrRaw(api.getCookies()));
        row.put("responseConfig", parseJsonOrRaw(api.getResponseConfig()));
        if (StrUtil.isNotBlank(api.getTestValueConfig())) {
            row.put("testValueConfig", parseJsonOrRaw(api.getTestValueConfig()));
        }
        if (StrUtil.isNotBlank(api.getBizCodeConfig())) {
            row.put("bizCodeConfig", parseJsonOrRaw(api.getBizCodeConfig()));
        }
        row.put("authConfig", parseAuthConfigObject(api.getAuthConfig()));
        if (StrUtil.isNotBlank(api.getDesignHints())) {
            row.put("designHints", parseJsonOrRaw(api.getDesignHints()));
        }
        row.put("syncProtected", api.getSyncProtected() != null ? api.getSyncProtected() : 1);
        if (StrUtil.isNotBlank(api.getPreRequestScript())) {
            row.put("preRequestScript", api.getPreRequestScript());
        }
        if (StrUtil.isNotBlank(api.getPostRequestScript())) {
            row.put("postRequestScript", api.getPostRequestScript());
        }
        return row;
    }

    private static Object parseAuthConfigObject(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Map.of("mode", "none");
        }
        try {
            ApiAuthConfig cfg = ApiAuthConfigSupport.parseOrInherit(raw);
            if (cfg == null || StrUtil.isBlank(cfg.getMode())) {
                return Map.of("mode", "none");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", cfg.getMode());
            if (StrUtil.isNotBlank(cfg.getAuthProfileId())) {
                out.put("authProfileId", cfg.getAuthProfileId());
            }
            if (cfg.getHeader() != null) {
                out.put("header", cfg.getHeader());
            }
            return out;
        } catch (Exception e) {
            return Map.of("mode", "none");
        }
    }

    private static Object parseJsonOrRaw(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String text = raw.trim();
        try {
            return JSONUtil.parse(text);
        } catch (Exception e) {
            return text;
        }
    }

    private static Object parseGraphObject(String graphJson) {
        if (StrUtil.isBlank(graphJson)) {
            return Map.of("nodes", List.of(), "edges", List.of());
        }
        try {
            return JSONUtil.parse(graphJson);
        } catch (Exception e) {
            return graphJson;
        }
    }

    private static Object parseEnvVariables(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        try {
            return JSONUtil.parse(raw.trim());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Object matchConfigOf(Match match) {
        if (match == null || match.getPathPrefix() == null || match.getPathPrefix().isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pathPrefix", new ArrayList<>(match.getPathPrefix()));
        return out;
    }

    private static void collectAssetKeysFromApi(TestProjectApi api, Set<String> keys) {
        if (api == null) {
            return;
        }
        PrefabricatedTemplateExtrasSupport.collectAssetKeys(api.getRequestConfig(), keys);
        PrefabricatedTemplateExtrasSupport.collectAssetKeys(api.getHeaders(), keys);
        PrefabricatedTemplateExtrasSupport.collectAssetKeys(api.getTestValueConfig(), keys);
        PrefabricatedTemplateExtrasSupport.collectAssetKeys(api.getPreRequestScript(), keys);
        PrefabricatedTemplateExtrasSupport.collectAssetKeys(api.getPostRequestScript(), keys);
    }

    private static Long parseLongId(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
