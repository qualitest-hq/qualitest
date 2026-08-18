package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 给已有项目追加鉴权模板时的请求体。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApplyAuthTemplatesParams {

    /** 要追加的模板 id，按数组顺序拷贝。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> templateIds;
}
