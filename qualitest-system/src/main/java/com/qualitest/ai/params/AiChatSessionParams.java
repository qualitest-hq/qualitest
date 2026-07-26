package com.qualitest.ai.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * AI 会话 Params 对象
 *
 * @author qualitest
 * @since 2026-06-15
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiChatSessionParams")
public class AiChatSessionParams extends BaseEntity implements Serializable {

    /**
     * 项目ID
     */
    private Long testProjectId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话场景
     */
    private String sessionScene;

    /**
     * 当前模型ID
     */
    private Long currentModelId;

    /**
     * 会话标题
     */
    private String sessionTitle;

    /**
     * 业务锚点 JSON（精确匹配，如 testFlowId）
     */
    private String bizRefJson;

    /**
     * 测试流 ID（从 biz_ref_json.testFlowId 提取匹配，用于会话列表查询）
     */
    private Long testFlowId;

    /**
     * 项目 API ID（从 biz_ref_json.testProjectApiId 提取匹配，用于 API 脚本助手会话列表查询）
     */
    private Long testProjectApiId;

}
