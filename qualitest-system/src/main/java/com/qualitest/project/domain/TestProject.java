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
     * 是否允许 MCP 全自动写流：开启后持 Token 方可经 MCP 改图画布并跑流。
     */
    private Boolean mcpAutopilotEnabled;

    /**
     * 是否允许 MCP 导入接口。
     * 开启后，持项目 Token 可调用 import_apis，向本项目接口库做方法+path 幂等写入。
     * 默认关闭。本字段只控制导入接口，不控制改图画布与跑流。
     */
    private Boolean mcpImportApisEnabled;

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
