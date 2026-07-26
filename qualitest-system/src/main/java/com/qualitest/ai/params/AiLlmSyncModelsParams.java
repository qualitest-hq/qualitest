package com.qualitest.ai.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * 批量同步模型入库请求，将选中的远端模型 ID 写入本地 ai_llm_model 表。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiLlmSyncModelsParams implements Serializable {

    /** 要同步的远端模型 ID 列表 */
    private List<String> modelIds;
}
