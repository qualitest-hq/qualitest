package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 测试流图 schema 升级请求。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeTestFlowGraphParams {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;
}
