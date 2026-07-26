package com.qualitest.project.params;

import lombok.*;

import java.io.Serializable;
import java.util.List;

/**
 * 测试项目环境批量排序参数（拖动排序专用）
 *
 * @author qualitest
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestProjectEnvReorderParams implements Serializable {

    /**
     * 测试项目 ID
     */
    private Long testProjectId;

    /**
     * 环境 ID 顺序（从前到后对应 sort_num = 0,1,2,...）
     */
    private List<Long> orderedEnvIds;
}
