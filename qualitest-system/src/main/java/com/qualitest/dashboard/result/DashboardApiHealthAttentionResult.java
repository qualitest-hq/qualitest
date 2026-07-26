package com.qualitest.dashboard.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.List;

/**
 * 接口变更待关注接口的返回体。
 * <p>
 * total 为当前用户可见范围内告警流总数；list 为按告警数排序后的截断列表（最多 20 条）。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("DashboardApiHealthAttentionResult")
public class DashboardApiHealthAttentionResult implements Serializable {

    /** 告警流总数（可大于 list 长度） */
    private Long total;

    /** 待关注流列表，最多 20 条 */
    private List<DashboardApiHealthAttentionItem> list;
}
