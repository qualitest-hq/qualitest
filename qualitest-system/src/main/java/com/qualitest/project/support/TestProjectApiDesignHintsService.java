package com.qualitest.project.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * API 造流设计提示（表列 design_hints）的读写。
 * <p>
 * 列内存 JSON：hints（短文本列表）、source、updatedAt。
 * 人机均可维护；插件/API 批量导入不修改本列。
 * get_api_detail 将 hints 注入造流模型。
 */
@Service
@RequiredArgsConstructor
public class TestProjectApiDesignHintsService {

    public static final int MAX_HINT_CHARS = 200;
    public static final int MAX_HINT_COUNT = 20;
    public static final int MAX_JSON_BYTES = 2048;
    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_AI_DESIGN = "ai_design";

    private final TestProjectApiMapper testProjectApiMapper;

    /**
     * 追加设计提示：与已有 hints 合并去重后写库。
     *
     * @return 写库后的完整 design_hints JSON
     */
    @Transactional(rollbackFor = Exception.class)
    public String appendHints(Long testProjectApiId, List<String> newHints, String source) {
        TestProjectApi api = requireApi(testProjectApiId);
        List<String> existing = readHintList(api.getDesignHints());
        List<String> merged = mergeHints(existing, normalizeIncoming(newHints));
        return persist(api.getTestProjectApiId(), merged, source);
    }

    /**
     * 全量替换设计提示（接口详情手改）。
     *
     * @return 写库后的完整 design_hints JSON
     */
    @Transactional(rollbackFor = Exception.class)
    public String replaceHints(Long testProjectApiId, List<String> hints, String source) {
        TestProjectApi api = requireApi(testProjectApiId);
        List<String> normalized = normalizeIncoming(hints);
        if (normalized.size() > MAX_HINT_COUNT) {
            normalized = new ArrayList<>(normalized.subList(0, MAX_HINT_COUNT));
        }
        return persist(api.getTestProjectApiId(), normalized, source);
    }

    /** 从实体/原始 JSON 解析 hints 列表；坏 JSON 返回空列表。 */
    public static List<String> readHintList(String raw) {
        JSONObject config = parseConfig(raw);
        JSONArray arr = config.getJSONArray("hints");
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            String h = StrUtil.trimToEmpty(arr.getString(i));
            if (!h.isEmpty()) {
                out.add(h);
            }
        }
        return out;
    }

    private String persist(Long testProjectApiId, List<String> hints, String source) {
        List<String> capped = enforceJsonByteCap(hints);
        JSONObject config = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.addAll(capped);
        config.put("hints", arr);
        config.put("source", StrUtil.isNotBlank(source) ? source.trim() : SOURCE_MANUAL);
        config.put("updatedAt", DateUtils.getTime());
        String json = config.toJSONString();
        TestProjectApi update = new TestProjectApi();
        update.setTestProjectApiId(testProjectApiId);
        update.setDesignHints(json);
        update.setUpdateTime(new Date());
        testProjectApiMapper.updateTestProjectApi(update);
        return json;
    }

    private TestProjectApi requireApi(Long testProjectApiId) {
        if (testProjectApiId == null) {
            throw new IllegalArgumentException("testProjectApiId 不能为空");
        }
        TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(testProjectApiId);
        if (api == null) {
            throw new IllegalArgumentException("接口不存在");
        }
        return api;
    }

    static List<String> normalizeIncoming(List<String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : incoming) {
            String h = StrUtil.trimToEmpty(raw);
            if (h.isEmpty()) {
                continue;
            }
            if (h.length() > MAX_HINT_CHARS) {
                h = h.substring(0, MAX_HINT_CHARS);
            }
            out.add(h);
        }
        return out;
    }

    static List<String> mergeHints(List<String> existing, List<String> incoming) {
        Set<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            merged.addAll(existing);
        }
        if (incoming != null) {
            for (String h : incoming) {
                if (merged.size() >= MAX_HINT_COUNT) {
                    break;
                }
                merged.add(h);
            }
        }
        return new ArrayList<>(merged);
    }

    /** 若整段 JSON 超 2KB，从末尾丢弃 hints 直至落入上限。 */
    static List<String> enforceJsonByteCap(List<String> hints) {
        List<String> list = new ArrayList<>(hints != null ? hints : List.of());
        while (!list.isEmpty() && buildProbeJson(list).getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            list.remove(list.size() - 1);
        }
        return list;
    }

    private static String buildProbeJson(List<String> hints) {
        JSONObject config = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.addAll(hints);
        config.put("hints", arr);
        config.put("source", SOURCE_MANUAL);
        config.put("updatedAt", "2026-01-01 00:00:00");
        return config.toJSONString();
    }

    static JSONObject parseConfig(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new JSONObject();
        }
        try {
            JSONObject obj = JSON.parseObject(raw);
            return obj != null ? obj : new JSONObject();
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
