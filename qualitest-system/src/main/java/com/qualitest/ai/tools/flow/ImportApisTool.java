package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.api.params.ApiImportParams;
import com.qualitest.api.result.ApiImportResult;
import com.qualitest.api.service.IApiImportService;
import com.qualitest.api.util.ApiImportMatchSupport;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 工具：把结构化接口列表写入当前项目的接口库。
 * <p>
 * 匹配键为 HTTP 方法 + 规范化 path；已存在则更新，不存在则新增。
 * 更新时若本次未传分组或注释，则保留库里原有值。
 * 同一请求内重复的方法+path 记入 conflicts，不落库。
 * 不会改写接口上的设计提示字段。
 * 受保护同步状态的接口由导入服务跳过，回执归入 skipped。
 */
@RequiredArgsConstructor
public class ImportApisTool implements QualitestTool {

    /** 项目接口导入落库 */
    private final IApiImportService apiImportService;
    /** 读取项目已有接口，用于方法+path 预匹配与元数据保留 */
    private final TestProjectApiMapper testProjectApiMapper;

    @Override
    public String getName() {
        return FlowDesignToolNames.IMPORT_APIS.getId();
    }

    /**
     * 执行导入。
     * 解析 items → 本批去重 → 与库内接口对齐 path/元数据 → 调用导入服务 → 组装回执。
     * 回执含 created / updated / skipped / conflicts，以及可选 warnings。
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long projectId = ctx.getTestProjectId();
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        McpApiImportItemMapper.MappedBatch mapped;
        try {
            mapped = McpApiImportItemMapper.toImportParams(arguments.get("items"));
        } catch (ServiceException e) {
            return FlowDesignToolSupport.errorJson(e.getMessage());
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("items 解析失败: " + e.getMessage());
        }

        // 库内已有接口：键为「METHOD 规范化path」
        Map<String, TestProjectApi> existingByNormIdentity = loadNormalizedIndex(projectId);

        List<ApiImportParams.ApiImportItem> toImport = new ArrayList<>();
        List<JSONObject> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        // 本批内已出现过的方法+path，用于拦截重复项
        Set<String> batchSeen = new HashSet<>();
        // 回执里标注分组/注释来源（code / preserved / default / empty）
        Map<String, ItemMeta> metaByIdentity = new LinkedHashMap<>();

        List<ApiImportParams.ApiImportItem> items = mapped.params().getApiList();
        List<McpApiImportItemMapper.MetaFlags> flags = mapped.metaFlags();
        for (int i = 0; i < items.size(); i++) {
            ApiImportParams.ApiImportItem item = items.get(i);
            McpApiImportItemMapper.MetaFlags flag = flags.get(i);
            String identity = flag.method() + " " + flag.normalizedPath();
            if (!batchSeen.add(identity)) {
                conflicts.add(ackRow(flag.method(), flag.normalizedPath(), flag.name(), null, "本批重复 METHOD+path，已跳过", null));
                continue;
            }

            TestProjectApi existing = existingByNormIdentity.get(identity);
            String groupSource;
            String descSource;
            if (existing != null) {
                // 沿用库内 path 原文，避免规范化后的字符串与库内不一致导致被当成新增
                item.setApiPath(existing.getApiPath());
                groupSource = applyMetaField(flag.groupProvided(), item::setApiGroup, existing.getApiGroup(), "preserved", "code");
                descSource = applyMetaField(flag.descriptionProvided(), item::setApiDescription, existing.getApiDescription(), "preserved", "code");
            } else {
                groupSource = flag.groupProvided() ? "code" : "default";
                descSource = flag.descriptionProvided() ? "code" : "empty";
            }
            ItemMeta meta = new ItemMeta(groupSource, descSource);
            metaByIdentity.put(identity, meta);
            if ("default".equals(groupSource)) {
                warnings.add(flag.method() + " " + flag.normalizedPath()
                        + "：未提供分组，将落入默认分组");
            }
            toImport.add(item);
        }

        if (toImport.isEmpty() && conflicts.isEmpty()) {
            return FlowDesignToolSupport.errorJson("没有可导入的接口");
        }

        JSONArray created = new JSONArray();
        JSONArray updated = new JSONArray();
        JSONArray skipped = new JSONArray();

        if (!toImport.isEmpty()) {
            ApiImportParams params = ApiImportParams.builder()
                    .configVersion(mapped.params().getConfigVersion())
                    .apiList(toImport)
                    .build();
            ApiImportResult result = apiImportService.importApis(projectId, resolveUserId(), params);
            if (result.getDetails() != null) {
                for (ApiImportResult.ApiImportDetail detail : result.getDetails()) {
                    String method = ApiImportMatchSupport.extractHttpMethod(findRequestConfig(toImport, detail));
                    String normPath = ProjectAuthConfigSupport.normalizeApiPath(detail.getApiPath());
                    ItemMeta meta = metaByIdentity.get(method + " " + normPath);
                    JSONObject row = ackRow(
                            method,
                            normPath != null ? normPath : detail.getApiPath(),
                            detail.getApiName(),
                            detail.getTestProjectApiId(),
                            detail.getMessage(),
                            meta);
                    // insert=新增，update=更新，skip=受保护等跳过，其它归入 conflicts
                    switch (detail.getStatus() != null ? detail.getStatus() : "") {
                        case "insert" -> created.add(row);
                        case "update" -> updated.add(row);
                        case "skip" -> skipped.add(row);
                        default -> conflicts.add(row);
                    }
                }
            }
        }

        JSONObject ack = new JSONObject();
        ack.put("ok", true);
        JSONObject summary = new JSONObject();
        summary.put("created", created.size());
        summary.put("updated", updated.size());
        summary.put("skipped", skipped.size());
        summary.put("conflicts", conflicts.size());
        ack.put("summary", summary);
        ack.put("created", created);
        ack.put("updated", updated);
        ack.put("skipped", skipped);
        ack.put("conflicts", conflicts);
        if (!warnings.isEmpty()) {
            ack.put("warnings", new JSONArray(new ArrayList<>(new HashSet<>(warnings))));
        }
        return ToolResultByteFit.fitAck(ack, ctx.getMaxToolResultBytes());
    }

    /**
     * 处理更新场景下的分组或注释字段。
     * 未传入：把库内原值写回导入项，并返回 preserved。
     * 已传入：保留调用方值，并返回 code。
     *
     * @param provided       调用方是否显式传了该字段
     * @param setter         写回导入项的 setter
     * @param existingValue  库内原值
     * @param whenPreserved  未传入时的来源标记
     * @param whenCode       已传入时的来源标记
     * @return 元数据来源标记，写入回执
     */
    private static String applyMetaField(
            boolean provided,
            java.util.function.Consumer<String> setter,
            String existingValue,
            String whenPreserved,
            String whenCode) {
        if (!provided) {
            setter.accept(existingValue);
            return whenPreserved;
        }
        return whenCode;
    }

    /**
     * 加载项目未删除接口，建成「METHOD 规范化path → 接口」索引。
     * 同键多条时保留先遇到的一条。
     */
    private Map<String, TestProjectApi> loadNormalizedIndex(Long projectId) {
        TestProjectApi query = new TestProjectApi();
        query.setTestProjectId(projectId);
        query.setDelStatus(0);
        List<TestProjectApi> list = testProjectApiMapper.selectTestProjectApiList(query);
        Map<String, TestProjectApi> index = new HashMap<>();
        if (list == null) {
            return index;
        }
        for (TestProjectApi api : list) {
            String method = ApiImportMatchSupport.extractHttpMethod(api.getRequestConfig());
            String path = ProjectAuthConfigSupport.normalizeApiPath(api.getApiPath());
            index.putIfAbsent(method + " " + path, api);
        }
        return index;
    }

    /**
     * 从本批导入项中按规范化 path 取出 requestConfig，用于解析 HTTP 方法。
     * 本批已按方法+path 去重；找不到时回退第一条。
     */
    private static String findRequestConfig(List<ApiImportParams.ApiImportItem> items,
                                            ApiImportResult.ApiImportDetail detail) {
        if (detail.getApiPath() == null || items == null || items.isEmpty()) {
            return items == null || items.isEmpty() ? null : items.get(0).getRequestConfig();
        }
        String detailPath = ProjectAuthConfigSupport.normalizeApiPath(detail.getApiPath());
        for (ApiImportParams.ApiImportItem item : items) {
            if (detailPath.equals(ProjectAuthConfigSupport.normalizeApiPath(item.getApiPath()))) {
                return item.getRequestConfig();
            }
        }
        return items.get(0).getRequestConfig();
    }

    /**
     * 组装单条回执行：方法、path、名称、接口 id、消息，以及分组/注释来源。
     */
    private static JSONObject ackRow(
            String method,
            String path,
            String name,
            Long testProjectApiId,
            String message,
            ItemMeta meta) {
        JSONObject row = new JSONObject();
        row.put("method", method);
        row.put("path", path);
        row.put("name", name);
        if (testProjectApiId != null) {
            row.put("testProjectApiId", String.valueOf(testProjectApiId));
        }
        row.put("message", message);
        if (meta != null) {
            row.put("metaGroupSource", meta.groupSource());
            row.put("metaDescriptionSource", meta.descSource());
        }
        return row;
    }

    /** 取当前登录用户 id；MCP Token 场景下可能取不到，返回 null 交给导入服务处理。 */
    private static Long resolveUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 单条接口在回执中的元数据来源。
     *
     * @param groupSource 分组来源：code=本次传入，preserved=沿用库内，default=新增且未传
     * @param descSource  注释来源：code=本次传入，preserved=沿用库内，empty=新增且未传
     */
    private record ItemMeta(String groupSource, String descSource) {
    }
}
