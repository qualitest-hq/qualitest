package com.qualitest.project.params;

import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目素材库查询 Params
 *
 * @author qualitest
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectAssetParams")
public class TestProjectAssetParams implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属测试项目 ID（必填）
     */
    private Long testProjectId;

    /**
     * 素材键名模糊
     */
    private String key;

    /**
     * 备注模糊
     */
    private String remark;

}
