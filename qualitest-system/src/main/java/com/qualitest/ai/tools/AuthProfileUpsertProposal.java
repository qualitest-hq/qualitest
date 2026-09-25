package com.qualitest.ai.tools;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 项目鉴权 Profile 的一次写入提案。
 * <p>
 * 半自动：upsert 工具只生成提案，用户确认后才改项目 auth_config；
 * 全自动：工具内已写库，status 为 confirmed。
 */
@Getter
@Builder
public class AuthProfileUpsertProposal {

    /** 待用户确认 */
    public static final String STATUS_PENDING = "pending";
    /** 已确认并落盘（或全自动已直写） */
    public static final String STATUS_CONFIRMED = "confirmed";
    /** 用户已拒绝，不写库 */
    public static final String STATUS_REJECTED = "rejected";

    /** 新建 Profile */
    public static final String ACTION_CREATED = "created";
    /** 更新已有 Profile */
    public static final String ACTION_UPDATED = "updated";

    /** Profile id；更新时必填；新建时可空，服务端发号后再回填 */
    private final String profileId;

    /** 操作类型：created / updated */
    private final String action;

    /** 提案状态：pending / confirmed / rejected */
    private final String status;

    /** 改前字段快照（展示用，可空） */
    private final Map<String, Object> before;

    /**
     * 拟写入字段（浅合并）。
     * 可含 name、pathPrefix、headerName、headerValueTemplate 等。
     */
    private final Map<String, Object> patch;

    /** 改后预览快照（展示用） */
    private final Map<String, Object> after;

    /** 相对 before 发生变化的字段名列表，供前端卡片与无明细回执 */
    private final List<String> changedFields;
}
