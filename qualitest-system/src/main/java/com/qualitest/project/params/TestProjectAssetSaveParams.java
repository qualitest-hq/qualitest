package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 项目素材库新增/修改 Params
 *
 * @author qualitest
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TestProjectAssetSaveParams implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属测试项目 ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 条目 ID（修改时必填）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 素材键名
     */
    private String key;

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 须为 { "&lt;key&gt;": { ... } }，且仅一个与 key 同名的子键
     */
    private Map<String, Object> assets;

}
