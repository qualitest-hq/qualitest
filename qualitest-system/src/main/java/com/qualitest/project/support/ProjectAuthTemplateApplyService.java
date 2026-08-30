package com.qualitest.project.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.CredentialApi;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.PrefabricatedApi;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.ApiAuthConfigSupport;
import com.qualitest.api.util.ApiImportMatchSupport;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.api.util.RequestConfigImportNormalizer;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectApiGroupService;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.service.ITestProjectTemplateService;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport.DerivedCredential;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport.PrefabFlow;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport.PrefabParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把勾选的项目模板写入项目鉴权配置，并种子尚未存在的接口 / 参数 / 测试流。
 * <p>
 * 规则简述：
 * <ul>
 *   <li>同名 Profile 整份跳过；</li>
 *   <li>预制接口按 method+path 去重，已有则不插入、不改已有行；</li>
 *   <li>托管头与凭证目标由预制测试流 extracts 派生（优先 asset，如 Bearer {{asset.x.y}}）；</li>
 *   <li>预制参数：flow→场景 flowSeed，env→项目环境变量，asset→项目素材库；</li>
 *   <li>预制测试流按 flowName 去重后写入项目测试流，并尽量绑定项目接口 id。</li>
 * </ul>
 */
@Service
public class ProjectAuthTemplateApplyService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAuthTemplateApplyService.class);

    /** 空响应配置，种子接口时写入 response_config。 */
    private static final String EMPTY_RESPONSE_CONFIG = "{\"configVersion\":1,\"responses\":[]}";

    private final ITestProjectTemplateService testProjectTemplateService;
    private final TestProjectMapper testProjectMapper;
    private final ITestProjectApiService testProjectApiService;
    private final ITestProjectApiGroupService testProjectApiGroupService;
    private final ITestProjectService testProjectService;
    private final ITestFlowService testFlowService;
    private final ITestProjectEnvService testProjectEnvService;

    public ProjectAuthTemplateApplyService(
            ITestProjectTemplateService testProjectTemplateService,
            TestProjectMapper testProjectMapper,
            ITestProjectApiService testProjectApiService,
            ITestProjectApiGroupService testProjectApiGroupService,
            @Lazy ITestProjectService testProjectService,
            @Lazy ITestFlowService testFlowService,
            ITestProjectEnvService testProjectEnvService) {
        this.testProjectTemplateService = testProjectTemplateService;
        this.testProjectMapper = testProjectMapper;
        this.testProjectApiService = testProjectApiService;
        this.testProjectApiGroupService = testProjectApiGroupService;
        this.testProjectService = testProjectService;
        this.testFlowService = testFlowService;
        this.testProjectEnvService = testProjectEnvService;
    }

    /**
     * 按勾选顺序把模板写入项目。
     * 步骤：组装鉴权 Profile → 写项目 auth_config → 种子接口 → 种子测试流（含 flowSeed）→ 种子环境/素材变量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void apply(Long testProjectId, List<Long> templateIds) {
        if (testProjectId == null) {
            throw new ServiceException("项目不能为空");
        }
        List<Long> ids = uniqueKeepOrder(templateIds);
        if (ids.isEmpty()) {
            throw new ServiceException("须至少勾选一套项目模板");
        }
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        if (project == null) {
            throw new ServiceException("项目不存在");
        }
        ProjectAuthConfig current = ProjectAuthConfigSupport.parse(project.getAuthConfig());
        current.setAuthProfiles(current.getAuthProfiles() == null
                ? new ArrayList<>()
                : new ArrayList<>(current.getAuthProfiles()));

        Set<String> existingNames = new HashSet<>();
        Set<String> apiIdentities = new LinkedHashSet<>();
        for (ProjectAuthProfile profile : current.getAuthProfiles()) {
            if (profile == null) {
                continue;
            }
            if (StrUtil.isNotBlank(profile.getName())) {
                existingNames.add(profile.getName().trim());
            }
            collectApiIdentities(profile, apiIdentities);
        }

        List<PrefabricatedApi> toSeed = new ArrayList<>();
        List<TestProjectTemplate> appliedTemplates = new ArrayList<>();
        for (Long templateId : ids) {
            TestProjectTemplate template = testProjectTemplateService.selectTestProjectTemplateById(templateId);
            if (template == null || (template.getDelStatus() != null && template.getDelStatus() == 1)) {
                throw new ServiceException("模板不存在: " + templateId);
            }
            if (template.getEnableStatus() != null && template.getEnableStatus() == 0) {
                throw new ServiceException("模板已禁用: " + template.getTemplateName());
            }
            String name = StrUtil.trimToNull(template.getTemplateName());
            if (name == null) {
                throw new ServiceException("模板名称不能为空");
            }
            if (existingNames.contains(name)) {
                continue;
            }
            ProjectAuthProfile profile = toProfile(template, apiIdentities, toSeed);
            current.getAuthProfiles().add(profile);
            existingNames.add(name);
            appliedTemplates.add(template);
        }

        ProjectAuthConfig normalized = ProjectAuthConfigSupport.normalize(current);
        if (ProjectAuthConfigSupport.isEmpty(normalized)) {
            throw new ServiceException("须至少勾选一套项目模板");
        }
        TestProject update = new TestProject();
        update.setTestProjectId(testProjectId);
        update.setAuthConfig(ProjectAuthConfigSupport.toJson(normalized));
        update.setUpdateTime(DateUtils.getNowDate());
        testProjectMapper.updateTestProject(update);

        seedApis(testProjectId, toSeed);
        for (TestProjectTemplate template : appliedTemplates) {
            seedFlows(testProjectId, template);
            seedEnvParams(testProjectId, template);
            seedAssetParams(testProjectId, template);
        }
    }

    /**
     * 仅按鉴权配置里的预制接口插入项目接口（已有 method+path 跳过）。
     * 不处理预制参数、不处理预制测试流。
     */
    @Transactional(rollbackFor = Exception.class)
    public void seedPrefabricatedApis(Long testProjectId, ProjectAuthConfig config) {
        List<PrefabricatedApi> toSeed = new ArrayList<>();
        if (config != null && config.getAuthProfiles() != null) {
            for (ProjectAuthProfile profile : config.getAuthProfiles()) {
                if (profile != null && profile.getApis() != null) {
                    toSeed.addAll(profile.getApis());
                }
            }
        }
        seedApis(testProjectId, toSeed);
    }

    /**
     * 把一条模板转成项目鉴权 Profile。
     * 生成新 Profile id；名称用模板名；path 已存在的预制接口不放入 Profile、也不再种子；
     * 托管头与凭证优先由预制测试流 extracts 派生。
     */
    private ProjectAuthProfile toProfile(
            TestProjectTemplate template, Set<String> apiIdentities, List<PrefabricatedApi> toSeed) {
        Match match = null;
        if (StrUtil.isNotBlank(template.getMatchConfig()) && !"null".equals(template.getMatchConfig().trim())) {
            try {
                match = JSONUtil.toBean(template.getMatchConfig().trim(), Match.class);
            } catch (Exception e) {
                throw new ServiceException("模板 match_config 不是合法 JSON: " + template.getTemplateName());
            }
        }
        List<PrefabricatedApi> apis = parseApis(template);
        List<PrefabricatedApi> kept = new ArrayList<>();
        for (PrefabricatedApi api : apis) {
            if (api == null || StrUtil.isBlank(api.getApiPath())) {
                continue;
            }
            String identity = prefabricatedIdentity(api);
            if (!apiIdentities.add(identity)) {
                continue;
            }
            kept.add(api);
            toSeed.add(api);
        }

        DerivedCredential derived = PrefabricatedTemplateExtrasSupport.deriveCredential(
                template.getTemplateFlows());

        String headerName;
        String headerValueTemplate;
        CredentialApi credentialApi = null;
        if (derived != null) {
            credentialApi = derived.getCredentialApi();
            headerName = derived.getHeaderName();
            headerValueTemplate = derived.getHeaderValueTemplate();
        } else {
            // 没有任何凭证来源时写弱默认头，避免后续规范化因缺头失败
            headerName = "Authorization";
            headerValueTemplate = "Bearer {{asset.adminAuth.token}}";
        }

        return ProjectAuthProfile.builder()
                .id(String.valueOf(IdUtil.getSnowflakeNextId()))
                .name(template.getTemplateName().trim())
                .match(match)
                .headerName(headerName)
                .headerValueTemplate(headerValueTemplate)
                .credentialApi(credentialApi)
                .apis(kept)
                .build();
    }

    /** 解析模板上的预制接口 JSON；非法则抛业务异常。 */
    private List<PrefabricatedApi> parseApis(TestProjectTemplate template) {
        if (StrUtil.isBlank(template.getTemplateApis())) {
            return List.of();
        }
        try {
            return JSONUtil.toList(JSONUtil.parseArray(template.getTemplateApis()), PrefabricatedApi.class);
        } catch (Exception e) {
            throw new ServiceException("模板预制接口不是合法 JSON: " + template.getTemplateName());
        }
    }

    /** 把预制接口插入项目接口表；已有相同 method+path 则整条跳过。 */
    private void seedApis(Long testProjectId, List<PrefabricatedApi> toSeed) {
        if (toSeed == null || toSeed.isEmpty()) {
            return;
        }
        Map<String, TestProjectApi> existing = new HashMap<>();
        List<TestProjectApi> dbList = testProjectApiService.selectTestProjectApiList(
                TestProjectApi.builder().testProjectId(testProjectId).delStatus(0).build());
        if (dbList != null) {
            for (TestProjectApi api : dbList) {
                existing.putIfAbsent(ApiImportMatchSupport.buildIdentity(api), api);
            }
        }
        Map<String, Long> groupCache = new HashMap<>();
        List<TestProjectApi> inserts = new ArrayList<>();
        Date now = DateUtils.getNowDate();
        for (PrefabricatedApi prefab : toSeed) {
            if (prefab == null || StrUtil.isBlank(prefab.getApiPath())) {
                continue;
            }
            String requestJson = serializeRequestConfig(prefab);
            String identity = ApiImportMatchSupport.buildIdentity(requestJson, prefab.getApiPath());
            if (existing.containsKey(identity)) {
                continue;
            }
            TestProjectApi api = TestProjectApi.builder()
                    .testProjectApiId(IdUtil.getSnowflakeNextId())
                    .testProjectId(testProjectId)
                    .apiGroupId(TestProjectApiGroupResolveSupport.resolve(
                            testProjectApiGroupService, testProjectId, prefab.getApiGroup(), groupCache))
                    .apiStatus(StrUtil.blankToDefault(prefab.getApiStatus(), "normal"))
                    .apiGroup(StrUtil.blankToDefault(prefab.getApiGroup(), "默认分组"))
                    .apiName(StrUtil.blankToDefault(prefab.getApiName(), prefab.getApiPath()))
                    .apiDescription(StrUtil.trimToNull(prefab.getApiDescription()))
                    .apiPath(ProjectAuthConfigSupport.normalizeApiPath(prefab.getApiPath()))
                    .protocolType(StrUtil.blankToDefault(prefab.getProtocolType(), "http"))
                    .requestConfig(requestJson)
                    .headers(serializeJsonColumn(prefab.getHeaders(), "{}"))
                    .cookies(serializeJsonColumn(prefab.getCookies(), "{}"))
                    .responseConfig(serializeJsonColumn(prefab.getResponseConfig(), EMPTY_RESPONSE_CONFIG))
                    .testValueConfig(serializeJsonObject(prefab.getTestValueConfig()))
                    .bizCodeConfig(serializeJsonObject(prefab.getBizCodeConfig()))
                    .authConfig(serializeAuthConfig(prefab))
                    .designHints(serializeJsonObject(prefab.getDesignHints()))
                    .syncProtected(Integer.valueOf(1).equals(prefab.getSyncProtected()) ? 1 : 0)
                    .preRequestScript(StrUtil.trimToNull(prefab.getPreRequestScript()))
                    .postRequestScript(StrUtil.trimToNull(prefab.getPostRequestScript()))
                    .delStatus(0)
                    .build();
            api.setCreateTime(now);
            inserts.add(api);
            existing.put(identity, api);
        }
        if (!inserts.isEmpty()) {
            testProjectApiService.batchInsertTestProjectApi(inserts);
            testProjectService.refreshApiCount(testProjectId);
        }
    }

    /**
     * 按预制测试流种子项目测试流，并把同模板的 flow 参数写进默认场景 flowSeed。
     * 已有同名 flowName 跳过；写入前将合成 testProjectApiId remap 为项目接口 id（legacy 仍按 method+path）。
     */
    private void seedFlows(Long testProjectId, TestProjectTemplate template) {
        List<PrefabParam> flowParams = PrefabricatedTemplateExtrasSupport.parseParams(template.getTemplateParams())
                .stream()
                .filter(p -> "flow".equals(p.getKind()))
                .toList();

        List<PrefabFlow> flows = PrefabricatedTemplateExtrasSupport.parseFlows(template.getTemplateFlows());
        if (flows.isEmpty()) {
            return;
        }
        Set<String> existingNames = new HashSet<>();
        List<TestFlow> existing = testFlowService.selectTestFlowList(
                TestFlow.builder().testProjectId(testProjectId).delStatus(0).build());
        if (existing != null) {
            for (TestFlow flow : existing) {
                if (flow != null && StrUtil.isNotBlank(flow.getFlowName())) {
                    existingNames.add(flow.getFlowName().trim());
                }
            }
        }
        Map<String, Long> apiIdByIdentity = loadApiIdIndex(testProjectId);
        Map<String, Long> synthToProjectId = buildSynthApiIdMap(template, apiIdByIdentity);
        Date now = DateUtils.getNowDate();
        boolean firstSeeded = false;
        for (PrefabFlow prefab : flows) {
            if (existingNames.contains(prefab.getFlowName())) {
                continue;
            }
            String graphJson = prefab.getGraphJson();
            if (!firstSeeded) {
                graphJson = PrefabricatedTemplateExtrasSupport.mergeFlowSeedIntoGraph(graphJson, flowParams);
                firstSeeded = true;
            }
            graphJson = PrefabricatedTemplateExtrasSupport.bindGraphApis(
                    graphJson,
                    (method, path) -> apiIdByIdentity.get(method + " " + path),
                    synthToProjectId);
            TestFlow flow = new TestFlow();
            flow.setTestFlowId(IdUtil.getSnowflakeNextId());
            flow.setTestProjectId(testProjectId);
            flow.setFlowName(prefab.getFlowName());
            flow.setFlowDescription(prefab.getDescription());
            flow.setGraphJson(graphJson);
            flow.setDelStatus(0);
            flow.setCreateTime(now);
            testFlowService.insertTestFlow(flow);
            existingNames.add(prefab.getFlowName());
        }
    }

    /**
     * 模板作者期 apiId → 项目接口主键（按 method+path 在项目索引中解析）。
     * 凡非空 testProjectApiId（雪花数字或历史 tpl_*）均入表。
     */
    private Map<String, Long> buildSynthApiIdMap(TestProjectTemplate template, Map<String, Long> apiIdByIdentity) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (PrefabricatedApi api : parseApis(template)) {
            if (api == null || StrUtil.isBlank(api.getApiPath())) {
                continue;
            }
            String synth = StrUtil.trimToNull(api.getTestProjectApiId());
            if (synth == null) {
                continue;
            }
            Long projectId = apiIdByIdentity.get(prefabricatedIdentity(api));
            if (projectId != null) {
                map.put(synth, projectId);
            }
        }
        return map;
    }

    /**
     * 把预制参数 kind=env 合并进项目默认/首个环境的 envVariables（同 key 不覆盖）。
     * baseUrl 且环境尚无有效 envUrl 时写入 envUrl。
     */
    private void seedEnvParams(Long testProjectId, TestProjectTemplate template) {
        List<PrefabParam> envParams = PrefabricatedTemplateExtrasSupport.parseParams(template.getTemplateParams())
                .stream()
                .filter(p -> "env".equals(p.getKind()))
                .toList();
        if (envParams.isEmpty()) {
            return;
        }
        List<TestProjectEnv> envs = testProjectEnvService.selectTestProjectEnvList(
                TestProjectEnv.builder().testProjectId(testProjectId).delStatus(0).build());
        if (envs == null || envs.isEmpty()) {
            log.warn("模板「{}」有 env 预制参数但项目无环境，跳过", template.getTemplateName());
            return;
        }
        TestProjectEnv target = envs.get(0);
        String nextVars = PrefabricatedTemplateExtrasSupport.mergeEnvVariables(
                target.getEnvVariables(), envParams);
        boolean varsChanged = nextVars != null && !nextVars.equals(target.getEnvVariables());
        String nextUrl = target.getEnvUrl();
        boolean urlChanged = false;
        for (PrefabParam param : envParams) {
            if (param == null || !"baseUrl".equals(param.getName())) {
                continue;
            }
            if (StrUtil.isNotBlank(target.getEnvUrl())) {
                break;
            }
            String value = param.getValue() != null ? String.valueOf(param.getValue()).trim() : "";
            if (StrUtil.isBlank(value)) {
                break;
            }
            nextUrl = value;
            urlChanged = true;
            break;
        }
        if (!varsChanged && !urlChanged) {
            return;
        }
        TestProjectEnv patch = new TestProjectEnv();
        patch.setTestProjectEnvId(target.getTestProjectEnvId());
        if (varsChanged) {
            patch.setEnvVariables(nextVars);
        }
        if (urlChanged) {
            patch.setEnvUrl(nextUrl);
        }
        patch.setUpdateTime(DateUtils.getNowDate());
        testProjectEnvService.updateTestProjectEnv(patch);
    }

    /**
     * 把预制参数 kind=asset 合并进项目 asset_variables（同 key 不覆盖）。
     */
    private void seedAssetParams(Long testProjectId, TestProjectTemplate template) {
        List<PrefabParam> assetParams = PrefabricatedTemplateExtrasSupport.parseParams(template.getTemplateParams())
                .stream()
                .filter(p -> "asset".equals(p.getKind()))
                .toList();
        if (assetParams.isEmpty()) {
            return;
        }
        String existing = testProjectMapper.selectAssetVariablesByTestProjectId(testProjectId);
        String next = PrefabricatedTemplateExtrasSupport.mergeAssetVariables(existing, assetParams);
        if (next == null || next.equals(existing)) {
            return;
        }
        TestProject patch = new TestProject();
        patch.setTestProjectId(testProjectId);
        patch.setAssetVariables(next);
        patch.setUpdateTime(DateUtils.getNowDate());
        testProjectMapper.updateAssetVariables(patch);
    }

    /** 构建项目内「METHOD 规范化路径 → 接口主键」索引，供种子流绑接口。 */
    private Map<String, Long> loadApiIdIndex(Long testProjectId) {
        Map<String, Long> index = new LinkedHashMap<>();
        List<TestProjectApi> dbList = testProjectApiService.selectTestProjectApiList(
                TestProjectApi.builder().testProjectId(testProjectId).delStatus(0).build());
        if (dbList == null) {
            return index;
        }
        for (TestProjectApi api : dbList) {
            if (api == null) {
                continue;
            }
            String identity = ApiImportMatchSupport.buildIdentity(api);
            index.putIfAbsent(identity, api.getTestProjectApiId());
        }
        return index;
    }

    /** 预制接口的 requestConfig 规范化后写成可落库 JSON。 */
    private String serializeRequestConfig(PrefabricatedApi prefab) {
        String raw;
        if (prefab.getRequestConfig() instanceof String s && StrUtil.isNotBlank(s)) {
            raw = s;
        } else if (prefab.getRequestConfig() != null) {
            raw = JSONUtil.toJsonStr(prefab.getRequestConfig());
        } else {
            raw = JSONUtil.toJsonStr(ProjectAuthConfigSupport.minimalRequestConfig("GET", null));
        }
        return RequestConfigImportNormalizer.normalize(raw);
    }

    /** 预制接口鉴权落库：只保留 mode / profileId / 自定义头，不落 loginHint。 */
    private String serializeAuthConfig(PrefabricatedApi prefab) {
        if (prefab.getAuthConfig() == null || StrUtil.isBlank(prefab.getAuthConfig().getMode())) {
            return ApiAuthConfigSupport.noneStorageJson();
        }
        ApiAuthConfig auth = prefab.getAuthConfig();
        ApiAuthConfig stored = ApiAuthConfig.builder()
                .mode(auth.getMode())
                .authProfileId(auth.getAuthProfileId())
                .header(auth.getHeader())
                .build();
        String json = ApiAuthConfigSupport.toStorageJson(stored);
        return json != null ? json : ApiAuthConfigSupport.noneStorageJson();
    }

    /** 对象或 JSON 字符串转成可写库 JSON；空则用缺省值。 */
    private String serializeJsonColumn(Object value, String defaultJson) {
        String json = serializeJsonObject(value);
        return json != null ? json : defaultJson;
    }

    /** 对象或 JSON 字符串转成可写库 JSON；空则 null。 */
    private String serializeJsonObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return StrUtil.isBlank(s) ? null : s;
        }
        return JSONUtil.toJsonStr(value);
    }

    /** 收集某 Profile 下已有预制接口的 method+path，用于去重。 */
    private void collectApiIdentities(ProjectAuthProfile profile, Set<String> identities) {
        if (profile == null || profile.getApis() == null) {
            return;
        }
        for (PrefabricatedApi api : profile.getApis()) {
            if (api != null && StrUtil.isNotBlank(api.getApiPath())) {
                identities.add(prefabricatedIdentity(api));
            }
        }
    }

    /** 预制接口去重键：METHOD + 规范化路径；缺 method 按 GET。 */
    private String prefabricatedIdentity(PrefabricatedApi api) {
        String method = ProjectAuthConfigSupport.prefabricatedHttpMethod(api);
        String path = ProjectAuthConfigSupport.normalizeApiPath(api.getApiPath());
        return (method != null ? method : "GET") + " " + path;
    }

    /** 模板 id 去重并保持原顺序。 */
    private List<Long> uniqueKeepOrder(List<Long> templateIds) {
        List<Long> out = new ArrayList<>();
        if (templateIds == null) {
            return out;
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : templateIds) {
            if (id != null && seen.add(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
