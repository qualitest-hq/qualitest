package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 给已有项目追加勾选项目模板时的请求体。
 * 后端按 templateIds 顺序写入鉴权 Profile，并种子接口 / 参数 / 测试流。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApplyAuthTemplatesParams {

    /** 要追加的模板主键列表（保持勾选顺序；同名 Profile 会跳过）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> templateIds;
}
