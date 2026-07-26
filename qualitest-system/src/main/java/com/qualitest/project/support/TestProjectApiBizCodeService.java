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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * API 业务响应码白名单（表列 biz_code_config）的读写。
 * <p>
 * 列内存 JSON，字段：successValues（成功 code 列表）、source（写入来源）、updatedAt。
 * 插件/API 批量导入不修改本列；由 Run 暂停确认或设置页通过专用接口追加。
 * HTTP 执行时若本列 successValues 非空，优先于项目响应约定中的成功值。
 */
@Service
@RequiredArgsConstructor
public class TestProjectApiBizCodeService {

    private final TestProjectApiMapper testProjectApiMapper;

    /**
     * 向指定 API 追加业务成功 code，与已有白名单合并去重后写库。
     *
     * @param testProjectApiId 目标 API 主键
     * @param newValues        待追加的 code；null 表示不追加新值，仍刷新 source、updatedAt
     * @param source           写入来源标识；空则记为 runtime
     * @return 写库后的完整 biz_code_config JSON 字符串
     */
    @Transactional(rollbackFor = Exception.class)
    public String appendSuccessValues(Long testProjectApiId, List<Integer> newValues, String source) {
        if (testProjectApiId == null) {
            throw new IllegalArgumentException("testProjectApiId 不能为空");
        }
        TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(testProjectApiId);
        if (api == null) {
            throw new IllegalArgumentException("接口不存在");
        }

        JSONObject config = parseBizCodeConfig(api.getBizCodeConfig());
        JSONArray successValues = config.getJSONArray("successValues");
        if (successValues == null) {
            successValues = new JSONArray();
            config.put("successValues", successValues);
        }

        Set<Integer> merged = new LinkedHashSet<>();
        for (int i = 0; i < successValues.size(); i++) {
            Integer v = successValues.getInteger(i);
            if (v != null) {
                merged.add(v);
            }
        }
        if (newValues != null) {
            for (Integer v : newValues) {
                if (v != null) {
                    merged.add(v);
                }
            }
        }
        JSONArray normalized = new JSONArray();
        normalized.addAll(merged);
        config.put("successValues", normalized);
        config.put("source", StrUtil.isNotBlank(source) ? source : "runtime");
        config.put("updatedAt", DateUtils.getTime());

        String json = config.toJSONString();
        TestProjectApi update = new TestProjectApi();
        update.setTestProjectApiId(testProjectApiId);
        update.setBizCodeConfig(json);
        update.setUpdateTime(new Date());
        testProjectApiMapper.updateTestProjectApi(update);
        return json;
    }

    /**
     * 读取接口 biz_code_config 中的成功业务码列表。
     * 无配置或解析失败时返回空列表。供 HTTP 执行路径直接调用，无需注入本 Service。
     */
    public static List<Integer> readSuccessValues(TestProjectApi api) {
        if (api == null || StrUtil.isBlank(api.getBizCodeConfig())) {
            return List.of();
        }
        JSONObject config = parseBizCodeConfig(api.getBizCodeConfig());
        JSONArray arr = config.getJSONArray("successValues");
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            Integer v = arr.getInteger(i);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * 解析 biz_code_config；空白或非法 JSON 时返回空对象，不抛异常。
     */
    private static JSONObject parseBizCodeConfig(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(raw);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
