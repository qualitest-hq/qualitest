package com.qualitest.ai.tools.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Match;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.CredentialTargetSupport;
import com.qualitest.api.util.CredentialTargetSupport.CredentialTarget;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.common.utils.spring.SpringUtils;
import com.qualitest.flow.sync.FlowExternalChangePublisher;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.support.ResponseConventionSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多端 Profile 的列表摘要、字段浅合并与落盘。
 * <p>
 * 负责：解析 AI 工具 patch、生成提案 before/after 快照、校验多套 Profile 凭证不碰撞、
 * 把合并结果写入 test_project.auth_config（含鉴权头与响应约定）。
 */
public final class AuthProfileUpsertSupport {

    private AuthProfileUpsertSupport() {}

    /**
     * 组装列举工具回执：items 为各 Profile 摘要，不含密钥明文。
     *
     * @param authConfigJson 项目 auth_config JSON
     * @return 含 items、truncated 的 JSON 对象
     */
    public static JSONObject buildListResult(String authConfigJson) {
        JSONObject result = new JSONObject();
        List<JSONObject> items = new ArrayList<>();
        ProjectAuthConfig config = ProjectAuthConfigSupport.parse(authConfigJson);
        if (config != null && config.getAuthProfiles() != null) {
            for (ProjectAuthProfile profile : config.getAuthProfiles()) {
                if (profile == null || StrUtil.isBlank(profile.getId())) {
                    continue;
                }
                items.add(toSummary(profile));
            }
        }
        result.put("items", items);
        result.put("truncated", false);
        return result;
    }

    /**
     * 将单个 Profile 转为列举摘要。
     * 含：id、名称、pathPrefix、托管头、规范化后的响应约定四字段、凭证目标。
     * 不含密钥明文。
     *
     * @param profile 多端 Profile
     * @return 摘要 JSON
     */
    public static JSONObject toSummary(ProjectAuthProfile profile) {
        JSONObject item = new JSONObject();
        item.put("id", profile.getId());
        item.put("name", profile.getName());
        List<String> prefixes = profile.getMatch() != null && profile.getMatch().getPathPrefix() != null
                ? profile.getMatch().getPathPrefix()
                : List.of();
        item.put("pathPrefix", prefixes);
        item.put("headerName", profile.getHeaderName());
        item.put("headerValueTemplate", profile.getHeaderValueTemplate());
        item.put("responseConvention", ResponseConventionSupport.toMap(profile.getResponseConvention()));
        CredentialTarget target = CredentialTargetSupport.primaryTarget(profile);
        if (target != null) {
            JSONObject ct = new JSONObject();
            ct.put("scope", target.scope());
            ct.put("entryKey", target.entryKey());
            ct.put("fieldPath", target.fieldPath());
            ct.put("flowKey", target.flowKey());
            ct.put("displayPath", target.displayPath());
            item.put("credentialTarget", ct);
        }
        return item;
    }

    /**
     * 生成提案 before/after 用的字段快照。
     * 含 id、name、pathPrefix、headerName、headerValueTemplate、responseConvention（四字段）。
     *
     * @param profile 可空；空则返回空 Map
     * @return 有序字段快照
     */
    public static Map<String, Object> snapshot(ProjectAuthProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (profile == null) {
            return map;
        }
        map.put("id", profile.getId());
        map.put("name", profile.getName());
        map.put("pathPrefix", profile.getMatch() != null ? profile.getMatch().getPathPrefix() : List.of());
        map.put("headerName", profile.getHeaderName());
        map.put("headerValueTemplate", profile.getHeaderValueTemplate());
        map.put("responseConvention", ResponseConventionSupport.toMap(profile.getResponseConvention()));
        return map;
    }

    /**
     * 将 patch 浅合并到指定 Profile 并写入项目 auth_config。
     * create=true 且找不到 profileId 时新建 Profile（雪花 id，默认响应约定四字段）；
     * 合并后校验多套 Profile 凭证目标不碰撞，并发布 auth_config 变更事件。
     *
     * @param testProjectMapper 项目 Mapper
     * @param projectId         项目 id
     * @param profileId         既有 Profile id；新建时可空
     * @param create            是否允许新建
     * @param patch             拟合并字段（可含 responseConvention）
     * @return 落盘后的 Profile id
     */
    public static String persistPatch(
            TestProjectMapper testProjectMapper,
            Long projectId,
            String profileId,
            boolean create,
            Map<String, Object> patch) {
        if (testProjectMapper == null || projectId == null) {
            throw new ServiceException("缺少项目");
        }
        if (patch == null || patch.isEmpty()) {
            throw new ServiceException("缺少 patch");
        }
        TestProject project = testProjectMapper.selectTestProjectById(projectId);
        if (project == null) {
            throw new ServiceException("项目不存在");
        }
        ProjectAuthConfig config = ProjectAuthConfigSupport.parse(project.getAuthConfig());
        List<ProjectAuthProfile> profiles = config.getAuthProfiles() == null
                ? new ArrayList<>()
                : new ArrayList<>(config.getAuthProfiles());

        ProjectAuthProfile target = ProjectAuthConfigSupport.findProfile(config, profileId);
        int index = target != null ? profiles.indexOf(target) : -1;
        if (target == null) {
            if (!create) {
                throw new ServiceException("未找到 Profile id=" + profileId);
            }
            target = ProjectAuthProfile.builder()
                    .id(String.valueOf(IdUtil.getSnowflakeNextId()))
                    .responseConvention(ResponseConventionSupport.toMap((String) null))
                    .apis(new ArrayList<>())
                    .build();
            profiles.add(target);
            index = profiles.size() - 1;
        }

        ProjectAuthProfile merged = applyPatch(target, patch);
        profiles.set(index, merged);
        assertNoCredentialCollision(profiles);

        config.setAuthProfiles(profiles);
        ProjectAuthConfig normalized = ProjectAuthConfigSupport.normalize(config);
        TestProject update = new TestProject();
        update.setTestProjectId(projectId);
        update.setAuthConfig(ProjectAuthConfigSupport.toJson(normalized));
        update.setUpdateTime(DateUtils.getNowDate());
        testProjectMapper.updateTestProject(update);
        try {
            SpringUtils.getBean(FlowExternalChangePublisher.class)
                    .publishAuthConfigChanged(projectId, FlowExternalChangeSourceHolder.getOrDefault());
        } catch (Exception ignored) {
            // 单元测试等无 Spring 上下文时跳过
        }
        return merged.getId();
    }

    /**
     * 把 patch 中出现的字段合并进 Profile，未出现的字段保持原值；apis 列表原样保留。
     * <p>
     * 可合并字段：name、headerName、headerValueTemplate、pathPrefix、
     * responseConvention（对约定四字段浅合并）。
     *
     * @param base  合并前的 Profile
     * @param patch 拟写入字段
     * @return 合并后的新 Profile 实例
     */
    public static ProjectAuthProfile applyPatch(ProjectAuthProfile base, Map<String, Object> patch) {
        String name = base.getName();
        String headerName = base.getHeaderName();
        String headerValueTemplate = base.getHeaderValueTemplate();
        Match match = base.getMatch();
        Map<String, Object> responseConvention = base.getResponseConvention();

        if (patch.containsKey("name")) {
            name = StrUtil.trimToNull(stringVal(patch.get("name")));
        }
        if (patch.containsKey("headerName")) {
            headerName = StrUtil.trimToNull(stringVal(patch.get("headerName")));
        }
        if (patch.containsKey("headerValueTemplate")) {
            headerValueTemplate = StrUtil.trimToNull(stringVal(patch.get("headerValueTemplate")));
        }
        if (patch.containsKey("pathPrefix")) {
            List<String> prefixes = toStringList(patch.get("pathPrefix"));
            match = prefixes.isEmpty() ? null : Match.builder().pathPrefix(prefixes).build();
        }
        if (patch.containsKey("responseConvention")) {
            Map<String, Object> convPatch = toObjectMap(patch.get("responseConvention"));
            if (convPatch != null && !convPatch.isEmpty()) {
                String beforeJson = ProjectAuthConfigSupport.conventionJsonOf(base);
                String merged = ResponseConventionSupport.mergePatchToJson(beforeJson, convPatch);
                responseConvention = ResponseConventionSupport.toMap(merged);
            }
        }

        return ProjectAuthProfile.builder()
                .id(base.getId())
                .name(name)
                .match(match)
                .headerName(headerName)
                .headerValueTemplate(headerValueTemplate)
                .responseConvention(responseConvention != null
                        ? new LinkedHashMap<>(responseConvention)
                        : null)
                .apis(base.getApis() != null ? base.getApis() : new ArrayList<>())
                .build();
    }

    /**
     * 校验多套 Profile 的托管头主凭证路径互不重复。
     * 客户端与管理端须使用不同的 asset/flow 键，否则抛业务异常。
     *
     * @param profiles 待检查的 Profile 列表
     */
    public static void assertNoCredentialCollision(List<ProjectAuthProfile> profiles) {
        Set<String> seen = new LinkedHashSet<>();
        for (ProjectAuthProfile profile : profiles) {
            CredentialTarget target = CredentialTargetSupport.primaryTarget(profile);
            if (target == null) {
                continue;
            }
            String key = target.identityKey();
            if (!seen.add(key)) {
                throw new ServiceException(
                        "多套 Profile 托管头指向同一凭证 " + target.displayPath()
                                + "；客户端与管理端须使用不同 asset/flow 键");
            }
        }
    }

    /**
     * 比较 before/after 快照，列出发生变化的字段名（忽略 id）。
     * Map / List 按 JSON 序列化结果比较，避免引用不同但内容相同被判为变更。
     *
     * @param before 改前快照
     * @param after  改后快照
     * @return 变更字段名列表
     */
    public static List<String> changedFieldNames(Map<String, Object> before, Map<String, Object> after) {
        Set<String> keys = new LinkedHashSet<>();
        if (before != null) {
            keys.addAll(before.keySet());
        }
        if (after != null) {
            keys.addAll(after.keySet());
        }
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            if ("id".equals(key)) {
                continue;
            }
            Object a = before == null ? null : before.get(key);
            Object b = after == null ? null : after.get(key);
            if (!snapshotValueEquals(a, b)) {
                changed.add(key);
            }
        }
        return changed;
    }

    private static boolean snapshotValueEquals(Object a, Object b) {
        if (a instanceof Map<?, ?> || b instanceof Map<?, ?>
                || a instanceof List<?> || b instanceof List<?>) {
            return JSON.toJSONString(a).equals(JSON.toJSONString(b));
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    /** 把任意对象收成可修改的 String→Object Map（支持 Map 或 JSON 字符串）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toObjectMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        if (raw instanceof String s && StrUtil.isNotBlank(s)) {
            try {
                JSONObject obj = JSON.parseObject(s);
                return obj != null ? new LinkedHashMap<>(obj) : null;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /** 将任意值转为去首尾空白的字符串；null 仍为 null */
    private static String stringVal(Object raw) {
        return raw == null ? null : String.valueOf(raw).trim();
    }

    /**
     * 将 pathPrefix 入参转为字符串列表。
     * 支持 List，或逗号 / 换行 / 中文逗号分隔的字符串。
     */
    private static List<String> toStringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    out.add(String.valueOf(item).trim());
                }
            }
        } else if (raw instanceof String s) {
            for (String part : s.split("[,\\n，]+")) {
                if (StrUtil.isNotBlank(part)) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    /**
     * 解析工具入参中的 patch 对象为可修改的 Map。
     *
     * @param raw 工具 arguments.patch
     * @return 非空 Map；非法或空则 null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parsePatch(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = String.valueOf(e.getKey()).trim();
            if (key.isEmpty()) {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out.isEmpty() ? null : out;
    }
}
