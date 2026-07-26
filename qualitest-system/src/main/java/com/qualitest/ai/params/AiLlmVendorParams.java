package com.qualitest.ai.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * AI 厂商 Params 对象
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
@Alias("AiLlmVendorParams")
public class AiLlmVendorParams extends BaseEntity implements Serializable {

    /**
     * 厂商名
     */
    private String vendorName;

    /**
     * 协议标识
     */
    private String provider;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Integer enableStatus;

}
