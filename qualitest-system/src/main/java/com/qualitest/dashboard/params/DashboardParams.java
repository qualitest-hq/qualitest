package com.qualitest.dashboard.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 首页仪表盘查询参数
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("DashboardParams")
public class DashboardParams implements Serializable {

    /**
     * 非管理员时限定为其参与的项目
     */
    private Long memberUserId;
}
