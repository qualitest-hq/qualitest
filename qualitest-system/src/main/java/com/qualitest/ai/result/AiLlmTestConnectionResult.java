package com.qualitest.ai.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 厂商连通性检测结果，验证 Base URL、密钥及模型发现是否可用。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiLlmTestConnectionResult implements Serializable {

    /**
     * 检测是否通过
     */
    private Boolean success;

    /**
     * 失败原因或成功提示
     */
    private String message;

    /**
     * 成功时发现到的模型数量
     */
    private Integer modelCount;
}
