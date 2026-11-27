package com.qualitest.project.params;

import lombok.Data;

import java.util.Map;

/**
 * 保存项目流检查严重性覆盖的请求体。
 */
@Data
public class InspectionProfileSaveParams {

    /** 测试项目 id；路径上已有项目 id 时可不依赖本字段 */
    private Long testProjectId;

    /**
     * 覆盖表：诊断码 → {@code ERROR}/{@code WARNING}/{@code OFF}。
     * 必须传对象；传空对象表示清空全部覆盖，恢复各检查项的目录默认严重性。
     */
    private Map<String, String> overrides;
}
