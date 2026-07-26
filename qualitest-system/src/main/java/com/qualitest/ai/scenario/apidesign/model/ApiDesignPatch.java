package com.qualitest.ai.scenario.apidesign.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 一次对话产出的结构化修改建议包。
 */
@Getter
@Setter
public class ApiDesignPatch {

    /** 中文摘要：本轮建议意图，同时可作为助手气泡正文 */
    private String summary;

    /** 可独立勾选的变更列表（脚本 / 约束 / 测值 / 说明） */
    private List<ApiDesignPatchChange> changes = new ArrayList<>();
}
