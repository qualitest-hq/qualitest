package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * 追加 API 业务响应码白名单的请求体。
 * <p>
 * 把一批成功码并入接口 biz_code_config；元素可为整数或字符串。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestProjectApiBizCodeParams implements Serializable {

    /**
     * 要追加的业务 code 列表（整数或字符串，如 200、SUCCESS）。
     * 与库中已有 successValues 合并去重，不会删除已有项。
     */
    private List<Object> successValues;

    /**
     * 写入来源标识（便于审计）。
     * 未传时服务端记为 runtime。
     */
    private String source;
}
