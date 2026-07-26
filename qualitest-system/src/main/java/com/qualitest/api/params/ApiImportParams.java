package com.qualitest.api.params;

import com.qualitest.api.util.ApiConfigJsonSupport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * API 批量导入请求参数。
 * <p>
 * 由 IDEA 插件或其他工具上传；{@code configVersion} 固定为 1，
 * {@code apiList} 中每项携带 requestConfig、responseConfig 等 JSON 字符串字段。
 *
 * @author qualitest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiImportParams implements Serializable {

    public static final int CONFIG_VERSION = ApiConfigJsonSupport.CONFIG_VERSION;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 上传包协议版本，必须为 1。
     */
    @NotNull(message = "configVersion 不能为空")
    @Builder.Default
    private Integer configVersion = CONFIG_VERSION;

    /**
     * 测试项目ID（从Token验证中获取，此处可选用于二次校验）
     */
    private Long testProjectId;

    /**
     * 接口信息列表（支持批量导入）
     */
    @Valid
    @NotEmpty(message = "接口列表不能为空")
    private List<ApiImportItem> apiList;

    /**
     * 单条接口导入项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiImportItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * API状态（normal/deprecated）
         */
        private String apiStatus;

        /**
         * API分组（支持多级，如 "用户相关.用户登录"）
         */
        private String apiGroup;

        /**
         * API名称
         */
        private String apiName;

        /**
         * API详细描述
         */
        private String apiDescription;

        /**
         * API路径
         */
        private String apiPath;

        /**
         * 协议类型（http/https等）
         */
        private String protocolType;

        /**
         * 请求配置 JSON（configVersion=1，含 queryParams、declaredHeaders、body 等）。
         */
        private String requestConfig;

        /**
         * 响应配置 JSON（configVersion=1，含 responses 数组）。
         */
        private String responseConfig;

        /**
         * 请求头配置（JSON字符串，导入可不传）
         */
        private String headers;

        /**
         * Cookie配置（JSON字符串，导入可不传）
         */
        private String cookies;

        /**
         * 前置操作脚本
         */
        private String preRequestScript;

        /**
         * 后置操作脚本
         */
        private String postRequestScript;

        /**
         * 来源系统标识（如 "idea-plugin", "postman", "swagger"）
         */
        private String sourceSystem;

        /**
         * 外部系统ID
         */
        private String externalId;

        /**
         * 最后同步时间
         */
        private Date lastSyncTime;
    }
}
