package com.qualitest.ai.tools;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 素材库单条写入提案。
 * <p>
 * AI 调用 upsert 工具后先形成本对象，不写入项目素材库；
 * 用户在聊天侧确认后才落盘，拒绝则只改状态不落盘。
 */
@Getter
@Builder
public class AssetUpsertProposal {

    /** 用户尚未处理 */
    public static final String STATUS_PENDING = "pending";
    /** 用户已确认，字段已写入素材库 */
    public static final String STATUS_CONFIRMED = "confirmed";
    /** 用户已拒绝，未写入素材库 */
    public static final String STATUS_REJECTED = "rejected";

    /** 提案产生时项目下尚无该 key，确认后走新增 */
    public static final String ACTION_CREATED = "created";
    /** 提案产生时项目下已有该 key，确认后走更新 */
    public static final String ACTION_UPDATED = "updated";

    /** 素材条目键名，如 clientAuth */
    private final String key;

    /**
     * 相对提案产生时库中是否已有该 key：created 或 updated。
     * 确认落盘时仍按当时库中实际是否存在决定 insert / update。
     */
    private final String action;

    /** 素材备注；可为空 */
    private final String remark;

    /**
     * 字段名到明文值的映射，如 mobile、password。
     * 保存在会话消息元数据中，供确认时写入素材库；返回给模型的 tool 回执不含这些明文。
     */
    private final Map<String, Object> fields;

    /** 提案处理状态：pending / confirmed / rejected */
    private final String status;
}
