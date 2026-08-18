package com.qualitest.api.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.api.params.ApiImportParams;
import com.qualitest.api.result.ApiImportResult;
import com.qualitest.api.service.IApiImportService;
import com.qualitest.api.result.ApiImportMergeResult;
import com.qualitest.api.service.ApiImportMergeService;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.util.ApiAuthConfigSupport;
import com.qualitest.api.util.ApiConfigJsonSupport;
import com.qualitest.api.util.ApiImportConfigPipeline;
import com.qualitest.api.util.ApiImportMatchSupport;
import com.qualitest.api.util.ApiImportUserConfigSupport;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.diagnose.ApiFlowHealthPersistService;
import com.qualitest.flow.diagnose.ApiFlowReferenceScanService;
import com.qualitest.flow.diagnose.ApiSyncImpactSummary;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.service.ITestProjectApiGroupService;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.support.ProjectAuthTemplateApplyService;
import com.qualitest.project.support.TestProjectApiGroupResolveSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 外部来源（如 IDEA 插件）的 API 批量导入服务。
 * <p>
 * 分两阶段：先逐条校验并组装内存对象，再批量新增、逐条更新已有记录。
 * 接口匹配键为 HTTP 方法 + apiPath。
 * <p>
 * 新增：上传包规范化并补 example 后全量写入。<br>
 * 更新：request/response 做结构合并；headers、cookies、前后置脚本本地非空则保留；
 * biz_code_config、design_hints、test_value_config 不由上传包整段替换（test_value_config 由合并服务按字段更新）；
 * 上传包若带 auth，则覆盖写入鉴权标签；命中项目匿名 path 则 mode=none；
 * inherit 且未指定 authProfileId 时按项目鉴权配置回填。
 * 若 uploadType=project 且项目鉴权配置为空，则写入通用单套 Bearer 种子（不分端、无匿名 path）。
 * 结束后刷新项目的 api_count 与 last_api_sync_time。
 * 若本批有更新成功的接口：扫描项目内测试流影响写入 syncImpact，并对受影响流回写 api_health_*。
 */
@Slf4j
@Service
public class ApiImportServiceImpl implements IApiImportService {

    @Autowired
    private ITestProjectApiService testProjectApiService;

    @Autowired
    private ITestProjectApiGroupService testProjectApiGroupService;

    @Autowired
    private ITestProjectService testProjectService;

    @Autowired
    @Lazy
    private ProjectAuthTemplateApplyService projectAuthTemplateApplyService;

    /** 已有 API 更新时做 request/response 结构合并 */
    @Autowired
    private ApiImportMergeService apiImportMergeService;

    /** 导入更新后，扫描哪些测试流引用了变更 API，并做语义告警统计 */
    @Autowired
    private ApiFlowReferenceScanService apiFlowReferenceScanService;

    /** 导入更新后，把受影响流的 api_health_* 写回库 */
    @Autowired
    private ApiFlowHealthPersistService apiFlowHealthPersistService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiImportResult importApis(Long projectId, Long userId, ApiImportParams params) {

        ApiImportResult result = ApiImportResult.builder().build();
        int requestSize = params.getApiList() != null ? params.getApiList().size() : 0;
        logImportBatchStats(projectId, params);

        validateImportEnvelope(params);

        // 项目级上传且鉴权配置为空时，写入默认 Bearer 三口，再给本批接口回填 authProfileId
        ProjectAuthConfig projectAuth = resolveProjectAuthForImport(projectId, params);

        // 一次查出项目下全部已有 API，导入匹配走内存索引，避免按路径逐条 SELECT
        Map<String, TestProjectApi> existingDbIndex = loadExistingApiIndex(projectId);
        log.info("API导入预加载: projectId={}, 已有接口数={}", projectId, existingDbIndex.size());

        // 本批已解析接口索引，键为「HTTP方法 + 路径」，用于同批去重
        Map<String, TestProjectApi> batchIdentityIndex = new HashMap<>();
        // 分组 (parentId + 名称) -> apiGroupId，避免重复查/建分组
        Map<String, Long> apiGroupCache = new HashMap<>();
        // 已加入更新队列的 API 主键，避免同一条库内记录重复 update
        Set<Long> queuedUpdateIds = new HashSet<>();
        // 待批量 INSERT 的新增列表
        List<TestProjectApi> toInsert = new ArrayList<>();
        // 待逐条 UPDATE 的已有记录列表
        List<TestProjectApi> toUpdate = new ArrayList<>();

        for (ApiImportParams.ApiImportItem item : params.getApiList()) {
            try {
                ApiImportResult.ApiImportDetail detail = prepareSingleApi(
                        projectId, userId, item, existingDbIndex, batchIdentityIndex,
                        apiGroupCache, toInsert, toUpdate, queuedUpdateIds, projectAuth);
                result.addDetail(detail);
            } catch (Exception e) {
                log.error("处理接口失败, apiPath={}, error={}", item.getApiPath(), e.getMessage(), e);

                ApiImportResult.ApiImportDetail detail = ApiImportResult.ApiImportDetail.builder()
                        .apiPath(item.getApiPath())
                        .apiName(item.getApiName())
                        .status("fail")
                        .message("处理失败: " + e.getMessage())
                        .build();
                result.addDetail(detail);
            }
        }

        if (!toInsert.isEmpty()) {
            // 新增记录一次性批量写入，避免全量导入时逐条 INSERT
            testProjectApiService.batchInsertTestProjectApi(toInsert);
            log.info("API导入批量新增: projectId={}, count={}", projectId, toInsert.size());
        }
        for (TestProjectApi api : toUpdate) {
            testProjectApiService.updateTestProjectApi(api);
        }

        if (result.getSuccessCount() > 0) {
            touchProjectAfterApiImport(projectId);
        }

        attachSyncImpact(projectId, result);
        logImportResult(projectId, requestSize, result);
        return result;
    }

    /**
     * 对本批 status=update 的 API：扫描项目内测试流影响，写入 result.syncImpact；
     * 再对全部受影响流回写 api_health_*。
     * <p>
     * 无更新成功项则跳过。扫描/回写异常只打日志，不影响导入本身的成功失败结果。
     */
    private void attachSyncImpact(Long projectId, ApiImportResult result) {
        if (result.getDetails() == null || result.getDetails().isEmpty()) {
            return;
        }
        Set<Long> updatedIds = result.getDetails().stream()
                .filter(d -> "update".equals(d.getStatus()) && d.getTestProjectApiId() != null)
                .map(ApiImportResult.ApiImportDetail::getTestProjectApiId)
                .collect(Collectors.toCollection(HashSet::new));
        if (updatedIds.isEmpty()) {
            return;
        }
        try {
            ApiSyncImpactSummary impact =
                    apiFlowReferenceScanService.diagnoseSyncImpact(projectId, updatedIds);
            result.setSyncImpact(impact);
            // 用本次扫描得到的完整流 id 集合回写，避免再按 API 扫一遍引用
            apiFlowHealthPersistService.refreshFlows(impact.allAffectedFlowIds);
        } catch (Exception e) {
            log.warn("API导入后影响扫描失败（导入本身已成功）: projectId={}, updatedApis={}, err={}",
                    projectId, updatedIds.size(), e.getMessage(), e);
        }
    }

    /**
     * 导入开始前写日志：提交条数、按「方法+路径」去重后的唯一接口数、
     * 是否存在完全重复的接口、是否存在同路径多 HTTP 方法（REST 常见情况）。
     */
    private void logImportBatchStats(Long projectId, ApiImportParams params) {
        if (params.getApiList() == null || params.getApiList().isEmpty()) {
            log.info("API导入开始: projectId={}, 提交数=0", projectId);
            return;
        }
        int requestSize = params.getApiList().size();
        Map<String, Long> identityCounts = params.getApiList().stream()
                .collect(Collectors.groupingBy(ApiImportMatchSupport::buildIdentity, Collectors.counting()));
        Map<String, Long> pathCounts = params.getApiList().stream()
                .filter(item -> StrUtil.isNotBlank(item.getApiPath()))
                .collect(Collectors.groupingBy(ApiImportParams.ApiImportItem::getApiPath, Collectors.counting()));

        int uniqueIdentities = identityCounts.size();
        long identityDuplicates = identityCounts.values().stream().mapToLong(c -> Math.max(0L, c - 1L)).sum();
        long pathDuplicateEntries = pathCounts.values().stream().mapToLong(c -> Math.max(0L, c - 1L)).sum();

        if (identityDuplicates > 0) {
            List<String> samples = identityCounts.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .limit(5)
                    .map(e -> e.getKey() + "×" + e.getValue())
                    .toList();
            log.warn("API导入批次含重复接口(方法+路径相同): projectId={}, 提交={}, 唯一接口={}, 重复条目={}, 示例={}",
                    projectId, requestSize, uniqueIdentities, identityDuplicates, samples);
        }
        if (pathDuplicateEntries > 0) {
            List<String> pathSamples = pathCounts.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .limit(5)
                    .map(e -> e.getKey() + "×" + e.getValue())
                    .toList();
            log.info("API导入批次含同路径多 HTTP 方法: projectId={}, 多方法路径数={}, 示例={}",
                    projectId, pathSamples.size(), pathSamples);
        }
        log.info("API导入开始: projectId={}, 提交数={}, 唯一(方法+路径)={}", projectId, requestSize, uniqueIdentities);
    }

    /**
     * 导入结束后写汇总日志。
     * 有失败项时最多输出前 20 条路径与原因；
     * 有受影响测试流时额外打一条影响汇总日志。
     */
    private void logImportResult(Long projectId, int requestSize, ApiImportResult result) {
        log.info("API导入完成: projectId={}, 提交={}, 处理={}, 新增={}, 更新={}, 失败={}",
                projectId, requestSize, result.getTotalCount(),
                result.getInsertCount(), result.getUpdateCount(), result.getFailCount());
        if (result.getFailCount() != null && result.getFailCount() > 0 && result.getDetails() != null) {
            result.getDetails().stream()
                    .filter(d -> "fail".equals(d.getStatus()))
                    .limit(20)
                    .forEach(d -> log.warn("API导入失败: projectId={}, path={}, name={}, reason={}",
                            projectId, d.getApiPath(), d.getApiName(), d.getMessage()));
            if (result.getFailCount() > 20) {
                log.warn("API导入失败项过多，仅记录前 20 条，projectId={}, failCount={}",
                        projectId, result.getFailCount());
            }
        }
        ApiSyncImpactSummary impact = result.getSyncImpact();
        if (impact != null && impact.affectedFlowCount > 0) {
            log.info("API导入影响: projectId={}, changedApis={}, affectedFlows={}, warnings={}",
                    projectId, impact.changedApiCount, impact.affectedFlowCount, impact.warningCount);
        }
    }

    /**
     * 导入成功后更新项目冗余字段：按库内实际条数刷新 api_count，并记录 last_api_sync_time。
     */
    private void touchProjectAfterApiImport(Long projectId) {
        testProjectService.refreshApiCount(projectId);
        Date now = DateUtils.getNowDate();
        TestProject project = new TestProject();
        project.setTestProjectId(projectId);
        project.setLastApiSyncTime(now);
        project.setUpdateTime(now);
        testProjectService.updateTestProject(project);
    }

    /**
     * 加载项目下全部未删除 API，按「HTTP方法 + 路径」建索引供导入匹配。
     */
    private Map<String, TestProjectApi> loadExistingApiIndex(Long projectId) {
        List<TestProjectApi> list = testProjectApiService.selectTestProjectApiList(
                TestProjectApi.builder()
                        .testProjectId(projectId)
                        .delStatus(0)
                        .build());
        Map<String, TestProjectApi> index = new HashMap<>();
        if (list == null || list.isEmpty()) {
            return index;
        }
        for (TestProjectApi api : list) {
            index.putIfAbsent(ApiImportMatchSupport.buildIdentity(api), api);
        }
        return index;
    }

    /**
     * 处理单条导入项：校验、判断新增或更新、填充字段，结果进入 toInsert 或 toUpdate，此时不写库。
     * <p>
     * 本批内重复「方法+路径」时覆盖内存对象并记为 update；库内已存在且方法+路径相同则加入更新队列。
     */
    private ApiImportResult.ApiImportDetail prepareSingleApi(
            Long projectId,
            Long userId,
            ApiImportParams.ApiImportItem item,
            Map<String, TestProjectApi> existingDbIndex,
            Map<String, TestProjectApi> batchIdentityIndex,
            Map<String, Long> apiGroupCache,
            List<TestProjectApi> toInsert,
            List<TestProjectApi> toUpdate,
            Set<Long> queuedUpdateIds,
            ProjectAuthConfig projectAuth) {
        validateApiItem(item);

        String identity = ApiImportMatchSupport.buildIdentity(item);
        TestProjectApi existingInBatch = batchIdentityIndex.get(identity);
        TestProjectApi existingInDb = existingInBatch == null ? existingDbIndex.get(identity) : null;

        TestProjectApi api;
        String action;

        if (existingInBatch != null) {
            api = existingInBatch;
            action = "update";
        } else if (existingInDb != null) {
            api = existingInDb;
            action = "update";
            if (queuedUpdateIds.add(api.getTestProjectApiId())) {
                toUpdate.add(api);
            }
        } else {
            api = new TestProjectApi();
            api.setTestProjectApiId(IdUtil.getSnowflakeNextId());
            api.setTestProjectId(projectId);
            api.setCreateTime(DateUtils.getNowDate());
            toInsert.add(api);
            action = "insert";
        }

        Long apiGroupId = TestProjectApiGroupResolveSupport.resolve(
                testProjectApiGroupService, projectId, item.getApiGroup(), apiGroupCache);
        // 更新：结构合并 + 覆盖层本地优先；新增：全量写入
        applyApiFields(api, item, apiGroupId, "update".equals(action), projectAuth);
        batchIdentityIndex.put(identity, api);

        return ApiImportResult.ApiImportDetail.builder()
                .apiPath(item.getApiPath())
                .apiName(item.getApiName())
                .status(action)
                .testProjectApiId(api.getTestProjectApiId())
                .message("insert".equals(action) ? "新增成功" : "更新成功")
                .build();
    }

    /**
     * 将单条上传项写入实体（仍在内存，尚未落库）。
     *
     * @param isUpdate true=已有接口：合并 request/response 与 test_value_config，覆盖层本地优先；
     *                 false=新接口：规范化后全量写入，含 headers/cookies/脚本；
     *                 无论新增或更新，上传包若带 auth 都会写入鉴权标签
     */
    private void applyApiFields(
            TestProjectApi api,
            ApiImportParams.ApiImportItem item,
            Long apiGroupId,
            boolean isUpdate,
            ProjectAuthConfig projectAuth) {
        api.setApiGroupId(apiGroupId);
        api.setApiGroup(item.getApiGroup());
        api.setApiName(item.getApiName());
        api.setApiDescription(item.getApiDescription());
        api.setApiPath(item.getApiPath());
        api.setProtocolType(StrUtil.isNotBlank(item.getProtocolType()) ? item.getProtocolType() : "http");

        if (isUpdate) {
            // 保留用户原先的启用/禁用状态，不被上传包覆盖
            String previousStatus = api.getApiStatus();
            ApiImportMergeResult merged = apiImportMergeService.merge(api, item.getRequestConfig(), item.getResponseConfig());
            api.setRequestConfig(merged.getRequestConfig());
            api.setResponseConfig(merged.getResponseConfig());
            api.setTestValueConfig(merged.getTestValueConfig());
            applyUserLayerFieldsOnUpdate(api, item);
            if (StrUtil.isNotBlank(previousStatus)) {
                api.setApiStatus(previousStatus);
            } else {
                api.setApiStatus(StrUtil.isNotBlank(item.getApiStatus()) ? item.getApiStatus() : "normal");
            }
        } else {
            api.setApiStatus(StrUtil.isNotBlank(item.getApiStatus()) ? item.getApiStatus() : "normal");
            api.setRequestConfig(ApiImportConfigPipeline.normalizeAndEnrichRequest(item.getRequestConfig()));
            api.setResponseConfig(ApiImportConfigPipeline.normalizeAndEnrichResponse(item.getResponseConfig()));
            api.setHeaders(defaultJsonIfBlank(item.getHeaders()));
            api.setCookies(defaultJsonIfBlank(item.getCookies()));
            api.setPreRequestScript(item.getPreRequestScript());
            api.setPostRequestScript(item.getPostRequestScript());
        }

        applyAuthConfig(api, item, projectAuth);

        api.setLastSyncTime(item.getLastSyncTime() != null ? item.getLastSyncTime() : new Date());
        api.setUpdateTime(DateUtils.getNowDate());
        api.setDelStatus(0);
    }

    /**
     * 项目级上传且当前为空时写入默认 Bearer 三口；已有配置不覆盖。
     */
    private ProjectAuthConfig resolveProjectAuthForImport(Long projectId, ApiImportParams params) {
        TestProject project = testProjectService.selectTestProjectById(projectId);
        ProjectAuthConfig existing = ProjectAuthConfigSupport.parse(
                project != null ? project.getAuthConfig() : null);
        boolean seedIfEmpty = params.getUploadType() != null
                && params.getUploadType().seedProjectAuthIfEmpty();
        if (seedIfEmpty && ProjectAuthConfigSupport.isEmpty(existing)) {
            ProjectAuthConfig seeded = ProjectAuthConfigSupport.defaultBearerTemplate();
            persistProjectAuthConfig(projectId, seeded);
            projectAuthTemplateApplyService.seedPrefabricatedApis(projectId, seeded);
            log.info("项目鉴权配置已写入通用 Bearer 种子: projectId={}, uploadType={}",
                    projectId, params.getUploadType().getCode());
            return seeded;
        }
        if (seedIfEmpty && !ProjectAuthConfigSupport.isEmpty(existing)) {
            log.info("项目鉴权配置已存在，跳过种子: projectId={}, uploadType={}",
                    projectId, params.getUploadType().getCode());
        }
        return existing;
    }

    /** 把规范化后的鉴权 JSON 写回项目。 */
    private void persistProjectAuthConfig(Long projectId, ProjectAuthConfig config) {
        TestProject update = new TestProject();
        update.setTestProjectId(projectId);
        update.setAuthConfig(ProjectAuthConfigSupport.toJson(
                ProjectAuthConfigSupport.normalize(config)));
        update.setUpdateTime(DateUtils.getNowDate());
        testProjectService.updateTestProject(update);
    }

    /**
     * 写入鉴权标签：上传包带了 auth 则覆盖库中值；未带则仅在免登口写入 none。
     * 免登口（预制 mode=none，配置空时用内置 /login 等路径）：非 none 一律改成 none。
     * inherit 且未指定 authProfileId 时按路径回填 Profile。
     */
    private void applyAuthConfig(
            TestProjectApi api,
            ApiImportParams.ApiImportItem item,
            ProjectAuthConfig projectAuth) {
        ApiAuthConfig auth = item.getAuth();
        String method = ApiImportMatchSupport.extractHttpMethod(item.getRequestConfig());
        boolean anon = ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(
                method, item.getApiPath(), projectAuth);

        if (auth == null || StrUtil.isBlank(auth.getMode())) {
            if (!anon) {
                return;
            }
            api.setAuthConfig(ApiAuthConfigSupport.noneStorageJson());
            return;
        }
        String mode = ApiAuthConfigSupport.canonicalizeMode(auth.getMode());
        if (mode == null) {
            ApiAuthConfigSupport.toStorageJson(auth);
            return;
        }
        String profileId = StrUtil.trimToNull(auth.getAuthProfileId());
        if (!ApiAuthConfig.MODE_NONE.equals(mode) && anon) {
            mode = ApiAuthConfig.MODE_NONE;
        } else if (ApiAuthConfig.MODE_INHERIT.equals(mode) && profileId == null) {
            profileId = ProjectAuthConfigSupport.resolveProfileId(item.getApiPath(), projectAuth);
        }
        if (ApiAuthConfig.MODE_NONE.equals(mode)) {
            profileId = null;
        }
        ApiAuthConfig toStore = ApiAuthConfig.builder()
                .mode(mode)
                .authProfileId(profileId)
                .loginHint(auth.getLoginHint())
                .build();
        String json = ApiAuthConfigSupport.toStorageJson(toStore);
        if (json != null) {
            api.setAuthConfig(json);
        }
    }

    /**
     * 更新导入时处理覆盖层：headers、cookies、前后置脚本本地非空则跳过写入。
     * biz_code_config、design_hints 本方法不读写。
     */
    private void applyUserLayerFieldsOnUpdate(TestProjectApi api, ApiImportParams.ApiImportItem item) {
        ApiImportUserConfigSupport.applyJsonFieldOnUpdate(
                api.getHeaders(), item.getHeaders(),
                json -> api.setHeaders(validateAndSetJson(json, "headers")));
        ApiImportUserConfigSupport.applyJsonFieldOnUpdate(
                api.getCookies(), item.getCookies(),
                json -> api.setCookies(validateAndSetJson(json, "cookies")));
        ApiImportUserConfigSupport.applyScriptFieldOnUpdate(
                api.getPreRequestScript(), item.getPreRequestScript(), api::setPreRequestScript);
        ApiImportUserConfigSupport.applyScriptFieldOnUpdate(
                api.getPostRequestScript(), item.getPostRequestScript(), api::setPostRequestScript);
    }

    /**
     * 新增时 JSON 字段的缺省：上传包有有效内容则校验后写入，否则写空对象 "{}"。
     */
    private String defaultJsonIfBlank(String jsonStr) {
        return ApiImportUserConfigSupport.isNonEmptyUserJson(jsonStr)
                ? validateAndSetJson(jsonStr, "json")
                : "{}";
    }

    /**
     * 验证并设置 JSON 字段
     */
    private String validateAndSetJson(String jsonStr, String fieldName) {
        if (StrUtil.isBlank(jsonStr)) {
            return "{}";
        }

        try {
            JsonNode node = ApiConfigJsonSupport.readTree(jsonStr);
            return ApiConfigJsonSupport.writeCompact(node);
        } catch (JsonProcessingException e) {
            log.warn("{} 字段不是有效的 JSON 格式, 使用空对象, error={}", fieldName, e.getMessage());
            return "{}";
        }
    }

    /**
     * 校验导入包 configVersion 必须为 1。
     */
    private void validateImportEnvelope(ApiImportParams params) {
        Integer version = params.getConfigVersion();
        if (version == null || version != ApiConfigJsonSupport.CONFIG_VERSION) {
            throw new ServiceException("configVersion 必须为 1");
        }
    }

    /**
     * 校验接口项的必填字段
     */
    private void validateApiItem(ApiImportParams.ApiImportItem item) {
        if (StrUtil.isBlank(item.getApiPath())) {
            throw new ServiceException("API路径不能为空");
        }
        if (StrUtil.isBlank(item.getApiName())) {
            throw new ServiceException("API名称不能为空");
        }
        if (StrUtil.isBlank(item.getApiGroup())) {
            // 允许为空，会使用默认分组
            item.setApiGroup("默认分组");
        }
    }
}
