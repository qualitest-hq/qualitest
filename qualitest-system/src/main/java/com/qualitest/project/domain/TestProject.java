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
     * 响应约定 / 业务 Code 库（JSON 字符串）。
     * 描述被测系统统一响应包装：
     * codePath（业务码字段）、successValues（成功码列表）、
     * messagePath（错误消息字段）、dataPath（业务数据包装字段）。
     * HTTP 节点默认按此校验 body 业务码；节点可将 successCheck.mode 设为 off 关闭。
     */
    private String responseConvention;

    /**
     * 项目鉴权配置 JSON。
     * authProfiles 数组（扁平头 + 预制 apis）；勾选模板后拷贝进本字段，跑流只读副本。
     */
    private String authConfig;

    /**
     * 新建时勾选的模板 id 列表（不落库；Apply 后写入 authConfig）。
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
