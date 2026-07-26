package com.qualitest.api.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 单条 API 更新合并时的过程摘要。
 * <p>
 * 记录参数增删、是否更新了 schema、保留了哪些用户值项，供单元测试断言。
 * 不写入导入 HTTP 返回体，也不用于插件通知。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiImportMergeSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本次相对本地新增的 query 参数名 */
    @Builder.Default
    private List<String> queryParamsAdded = new ArrayList<>();

    /** 上传包已删除、本地原先存在的 query 参数名 */
    @Builder.Default
    private List<String> queryParamsRemoved = new ArrayList<>();

    /** 本次相对本地新增的 path 参数名 */
    @Builder.Default
    private List<String> pathParamsAdded = new ArrayList<>();

    /** 上传包已删除、本地原先存在的 path 参数名 */
    @Builder.Default
    private List<String> pathParamsRemoved = new ArrayList<>();

    /** 本次是否对请求或响应结构做过合并更新 */
    @Builder.Default
    private boolean schemaUpdated = false;

    /**
     * 合并中保留未覆盖的用户配置标识。
     * 例如 bodyExample、responseExample:{响应id} 等。
     */
    @Builder.Default
    private List<String> userPreserved = new ArrayList<>();

    /**
     * 追加一项「用户配置已保留」标识；空串忽略，已存在则不去重追加第二次。
     */
    public void addUserPreserved(String field) {
        if (field != null && !field.isBlank() && !userPreserved.contains(field)) {
            userPreserved.add(field);
        }
    }
}
