package com.qualitest.project.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.util.Date;
import java.util.List;

/**
 * 测试项目对象 test_project
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProject")
public class TestProject extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 项目名
     */
    private String projectName;

    /**
     * 素材变量
     */
    private String assetVariables;

    /**
     * 项目多端配置 JSON。
     * authProfiles 数组（鉴权头 + 响应约定 + 预制 apis）；勾选模板后拷贝进本字段，跑流只读副本。
     */
    private String authConfig;

    /**
     * 是否允许 MCP 自动写流：开启后持 Token 方可经 MCP 新建流、改图画布（不含跑流）。
     */
    private Boolean mcpAutoWriteEnabled;

    /**
     * 是否允许 MCP 自动跑流：开启后持 Token 方可经 MCP 调用 run_test_flow。
     * 默认关闭。通常须同时开启写流；关写流时应一并关闭本字段。
     */
    private Boolean mcpAutorunEnabled;

    /**
     * 是否允许 MCP 导入接口。
     * 开启后，持项目 Token 可调用 import_apis，向本项目接口库做方法+path 幂等写入。
     * 默认关闭。本字段只控制导入接口，不控制改图画布与跑流。
     */
    private Boolean mcpImportApisEnabled;

    /**
     * 跑流并发模式，对应列 run_concurrency_mode。
     * parallel：多条 Run 可同时执行；
     * serial_by_env：同一测试环境同时只跑一条，其余进入该环境排队（默认）。
     */
    private String runConcurrencyMode;

    /**
     * 是否允许暂停：Input 等人填表时可落 paused。
     * 关闭后碰到输入节点直接失败，不写 paused。库缺省 1（允许）；Java null 按允许处理。
     * 开跑时写入 {@code FlowRunContext.pausePermitted}（开放 HTTP 入站除外，入站固定禁止暂停）。
     */
    private Boolean allowPause;

    /**
     * 开放 HTTP 入站是否允许外联 HTTP。
     * 仅影响匿名开放入站开跑时的 {@code externalHttpPermitted}；
     * 不影响已登录场景/套件按成员外联能力判定。库缺省 0；Java null 按禁止处理。
     */
    private Boolean openHttpAllowExternal;

    /**
     * 开跑前解析「允许暂停」：字段为 null 或 true 时允许；false 时禁止。
     */
    public boolean isPauseAllowed() {
        return allowPause == null || Boolean.TRUE.equals(allowPause);
    }

    /**
     * 开跑前解析「开放入站允许外联」：仅 true 时允许，null/false 禁止。
     */
    public boolean isOpenHttpExternalAllowed() {
        return Boolean.TRUE.equals(openHttpAllowExternal);
    }

    /**
     * 本项目流检查严重性覆盖，JSON 对象。
     * 键为诊断码（核心短码或 {@code 插件id/短码}），值为 {@code ERROR}/{@code WARNING}/{@code OFF}。
     * 未出现的码使用目录默认；锁定项即使写了覆盖也仍按 ERROR 生效。
     */
    private String inspectionProfile;

    /**
     * 新建项目时勾选的模板主键列表。
     * 仅请求入参、不落库；创建时据此写入 authConfig 并种子预制资产。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> templateIds;

    /**
     * 最新API同步时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastApiSyncTime;

    /**
     * 项目下未删除 API 的条数冗余字段，由 refreshApiCount 从 test_project_api 统计后回写，
     * 用于项目列表、首页展示，避免列表页每次 COUNT 子查询。
     */
    private Integer apiCount;

    /**
     * 所有者ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long ownerId;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
