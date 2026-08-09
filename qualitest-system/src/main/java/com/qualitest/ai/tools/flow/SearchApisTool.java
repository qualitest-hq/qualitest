package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.api.util.AuthHeaderHintSupport;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** search_apis：scopeApiIds 优先，再关键词 DB 搜索或列举项目 API */
@RequiredArgsConstructor
public class SearchApisTool implements QualitestTool {

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;

    @Override
    public String getName() {
        return FlowDesignToolNames.SEARCH_APIS.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        String keyword = FlowDesignToolSupport.stringArg(arguments.get("keyword"));
        int limit = FlowDesignToolSupport.resolveSearchLimit(arguments.get("limit"), ctx.getMaxSearchApis());
        List<TestProjectApi> matched = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        if (ctx.getScopeApiIds() != null) {
            for (Long id : ctx.getScopeApiIds()) {
                if (id == null || seen.contains(id)) {
                    continue;
                }
                TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(id);
                if (api != null && api.getDelStatus() != null && api.getDelStatus() == 0
                        && api.getTestProjectId() != null && api.getTestProjectId().equals(ctx.getTestProjectId())) {
                    if (keyword.isEmpty() || matchesKeyword(api, keyword)) {
                        matched.add(api);
                        seen.add(id);
                    }
                }
            }
        }

        if (keyword.isEmpty() && matched.size() < limit) {
            TestProjectApi probe = new TestProjectApi();
            probe.setTestProjectId(ctx.getTestProjectId());
            List<TestProjectApi> all = testProjectApiMapper.selectTestProjectApiList(probe);
            for (TestProjectApi api : all) {
                if (api.getDelStatus() != null && api.getDelStatus() != 0) {
                    continue;
                }
                if (seen.contains(api.getTestProjectApiId())) {
                    continue;
                }
                matched.add(api);
                seen.add(api.getTestProjectApiId());
                if (matched.size() >= limit * 2) {
                    break;
                }
            }
        } else if (!keyword.isEmpty()) {
            List<TestProjectApi> searched = testProjectApiMapper.searchApisByKeyword(
                    ctx.getTestProjectId(), keyword, limit * 2);
            for (TestProjectApi api : searched) {
                if (seen.contains(api.getTestProjectApiId())) {
                    continue;
                }
                matched.add(api);
                seen.add(api.getTestProjectApiId());
            }
        }

        matched = matched.stream()
                .sorted(Comparator.comparing(a -> a.getApiName() == null ? "" : a.getApiName()))
                .collect(Collectors.toList());

        boolean truncated = matched.size() > limit;
        if (truncated) {
            matched = matched.stream().limit(limit).collect(Collectors.toList());
        }

        String projectAuthJson = null;
        if (ctx.getTestProjectId() != null && testProjectMapper != null) {
            TestProject project = testProjectMapper.selectTestProjectById(ctx.getTestProjectId());
            if (project != null) {
                projectAuthJson = project.getAuthConfig();
            }
        }

        JSONArray items = new JSONArray();
        for (TestProjectApi api : matched) {
            JSONObject item = new JSONObject();
            item.put("id", String.valueOf(api.getTestProjectApiId()));
            item.put("method", FlowDesignApiSummarizer.resolveMethod(api));
            item.put("path", api.getApiPath());
            item.put("name", api.getApiName());
            item.put("auth", AuthHeaderHintSupport.compactAuth(
                    api.getAuthConfig(), projectAuthJson, api.getApiPath()));
            items.add(item);
        }
        String hint = truncated
                ? "匹配结果超过 limit=" + limit + "，请缩小关键词或使用 get_api_detail"
                : null;
        return FlowDesignToolSupport.buildItemsResult(items, truncated, hint, ctx.getMaxToolResultBytes());
    }

    private static boolean matchesKeyword(TestProjectApi api, String keyword) {
        String kw = keyword.toLowerCase(Locale.ROOT);
        return contains(api.getApiName(), kw) || contains(api.getApiPath(), kw)
                || contains(api.getApiDescription(), kw);
    }

    private static boolean contains(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }
}
