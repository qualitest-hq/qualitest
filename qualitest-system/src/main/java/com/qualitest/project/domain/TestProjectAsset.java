package com.qualitest.project.domain;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 项目素材变量
 *
 * @author qualitest
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TestProjectAsset implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 条目雪花 ID
     */
    private Long id;

    /**
     * 素材键名（项目内唯一）
     */
    private String key;

    /**
     * 备注说明（展示用，不参与占位符引用）
     */
    private String remark;

    /**
     * 最近保存时间 yyyy-MM-dd HH:mm:ss
     */
    private String updateTime;

    /**
     * 固定一层包装：仅含以 key 为名的子 object
     */
    private Map<String, Object> assets;

}
