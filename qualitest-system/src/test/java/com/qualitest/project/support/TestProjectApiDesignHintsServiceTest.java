/**
 * 测 TestProjectApiDesignHintsService：append/replace、去重、条数与字数上限。
 * 边界：Mock Mapper，无真实 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectApiDesignHintsServiceTest
 */
package com.qualitest.project.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectApiDesignHintsServiceTest {

    private static final long API_ID = 1001L;

    private TestProjectApiMapper mapper;
    private TestProjectApiDesignHintsService service;
    private TestProjectApi api;

    @BeforeEach
    void setUp() {
        mapper = mock(TestProjectApiMapper.class);
        service = new TestProjectApiDesignHintsService(mapper);
        api = TestProjectApi.builder().testProjectApiId(API_ID).build();
        when(mapper.selectTestProjectApiById(API_ID)).thenReturn(api);
        when(mapper.updateTestProjectApi(any())).thenReturn(1);
    }

    @Test
    @Order(1)
    @DisplayName("append 去重合并")
    void appendHints_dedupes() {
        // 前提：已有一条 hint；再 append 相同与新增各一
        // 期望：库中仅两条，source=ai_design
        api.setDesignHints("{\"hints\":[\"a\"],\"source\":\"manual\"}");
        String json = service.appendHints(API_ID, List.of("a", "b"), TestProjectApiDesignHintsService.SOURCE_AI_DESIGN);
        JSONObject obj = JSON.parseObject(json);
        assertEquals(List.of("a", "b"), obj.getList("hints", String.class));
        assertEquals(TestProjectApiDesignHintsService.SOURCE_AI_DESIGN, obj.getString("source"));
        ArgumentCaptor<TestProjectApi> cap = ArgumentCaptor.forClass(TestProjectApi.class);
        verify(mapper).updateTestProjectApi(cap.capture());
        assertEquals(json, cap.getValue().getDesignHints());
    }

    @Test
    @Order(2)
    @DisplayName("replace 全量替换并可清空")
    void replaceHints_replacesAll() {
        // 前提：已有 hints；replace 为空列表
        // 期望：hints 为空数组，source=manual
        api.setDesignHints("{\"hints\":[\"old\"],\"source\":\"ai_design\"}");
        String json = service.replaceHints(API_ID, List.of(), TestProjectApiDesignHintsService.SOURCE_MANUAL);
        JSONObject obj = JSON.parseObject(json);
        assertTrue(obj.getJSONArray("hints").isEmpty());
        assertEquals(TestProjectApiDesignHintsService.SOURCE_MANUAL, obj.getString("source"));
    }

    @Test
    @Order(3)
    @DisplayName("超过 20 条时截断")
    void appendHints_capsAtMaxCount() {
        // 前提：空库；一次 append 25 条不同 hint
        // 期望：落库最多 20 条
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add("hint-" + i);
        }
        String json = service.appendHints(API_ID, many, TestProjectApiDesignHintsService.SOURCE_MANUAL);
        assertEquals(20, JSON.parseObject(json).getJSONArray("hints").size());
    }

    @Test
    @Order(4)
    @DisplayName("单条超长截断到 200 字")
    void normalize_truncatesLongHint() {
        // 前提：一条 250 字符
        // 期望：落库长度为 200
        String longHint = "x".repeat(250);
        String json = service.replaceHints(API_ID, List.of(longHint), TestProjectApiDesignHintsService.SOURCE_MANUAL);
        String saved = JSON.parseObject(json).getJSONArray("hints").getString(0);
        assertEquals(200, saved.length());
    }

    @Test
    @Order(5)
    @DisplayName("坏 JSON 当作空 hints")
    void readHintList_badJson_empty() {
        // 前提：非法 JSON
        // 期望：空列表
        assertTrue(TestProjectApiDesignHintsService.readHintList("{bad").isEmpty());
        assertFalse(TestProjectApiDesignHintsService.readHintList(
                "{\"hints\":[\"ok\"]}").isEmpty());
    }
}
