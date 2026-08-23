package com.qualitest.project.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.PrefabricatedApi;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.ApiAuthConfigSupport;
import com.qualitest.api.util.ApiImportMatchSupport;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.api.util.RequestConfigImportNormalizer;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestProjectApiGroupService;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.service.ITestProjectTemplateService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把勾选的项目模板拷进项目 auth_config，并按预制接口插入尚未存在的项目接口。
 * <p>
 * 同名 Profile 整份跳过；method+path 已存在的预制口不写入该条、也不改已有接口。
 * Profile id 新生成，不沿用模板主键。跑流只读项目里这份副本。
 */
@Service
public class ProjectAuthTemplateApplyService {

    /** 空响应配置，种子接口时写入 response_config。 */
    private static final String EMPTY_RESPONSE_CONFIG = "{\"configVersion\":1,\"responses\":[]}";

    private final ITestProjectTemplateService testProjectTemplateService;
    private final TestProjectMapper testProjectMapper;
    private final ITestProjectApiService testProjectApiService;
    private final ITestProjectApiGroupService testProjectApiGroupService;
    private final ITestProjectService testProjectService;

    public ProjectAuthTemplateApplyService(
            ITestProjectTemplateService testProjectTemplateService,
            TestProjectMapper testProjectMapper,
            ITestProjectApiService testProjectApiService,
            ITestProjectApiGroupService testProjectApiGroupService,
            @Lazy ITestProjectService testProjectService) {
        this.testProjectTemplateService = testProjectTemplateService;
        this.testProjectMapper = testProjectMapper;
        this.testProjectApiService = testProjectApiService;
        this.testProjectApiGroupService = testProjectApiGroupService;
        this.testProjectService = testProjectService;
    }

    /**
     * 按勾选顺序把模板拷进项目：组装 Profile、写 auth_config、种子接口。
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
        if (current.getAuthProfiles() == null) {
            current.setAuthProfiles(new ArrayList<>());
        }

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
    }

    /**
     * 按配置里的预制接口插入项目接口。库里已有相同 method+path 的整条跳过。
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
     * 模板转成一条 Profile：新 id、名称取模板名；path 已存在的预制口丢掉不写入。
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
        return ProjectAuthProfile.builder()
                .id(String.valueOf(IdUtil.getSnowflakeNextId()))
                .name(template.getTemplateName().trim())
                .match(match)
                .headerName(template.getHeaderName())
                .headerValueTemplate(template.getHeaderValueTemplate())
                .apis(kept)
                .build();
    }

    /** 解析模板 apis JSON 数组。 */
    private List<PrefabricatedApi> parseApis(TestProjectTemplate template) {
        if (StrUtil.isBlank(template.getApis())) {
            return List.of();
        }
        try {
            return JSONUtil.toList(JSONUtil.parseArray(template.getApis()), PrefabricatedApi.class);
        } catch (Exception e) {
            throw new ServiceException("模板 apis 不是合法 JSON: " + template.getTemplateName());
        }
    }

    /** 把预制接口插入 test_project_api；已有 method+path 跳过，不改已有行。 */
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

    /** 预制口 requestConfig 转成规范化后的 JSON 字符串。 */
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

    /** 预制口鉴权转落库 JSON；只写 mode，不把 loginHint 落到接口行。 */
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

    /** 对象或 JSON 字符串转成可写库的 JSON；空则用缺省。 */
    private String serializeJsonColumn(Object value, String defaultJson) {
        String json = serializeJsonObject(value);
        return json != null ? json : defaultJson;
    }

    /** 对象或 JSON 字符串转成可写库的 JSON；空则 null。 */
    private String serializeJsonObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return StrUtil.isBlank(s) ? null : s;
        }
        return JSONUtil.toJsonStr(value);
    }

    /** 收集该 Profile 下已有预制口的 method+path。 */
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

    /** 预制口去重键：METHOD + 规范化路径；缺 method 按 GET。 */
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
