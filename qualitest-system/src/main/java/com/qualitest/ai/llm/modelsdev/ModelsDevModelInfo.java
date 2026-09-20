package com.qualitest.ai.llm.modelsdev;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * models.dev 中单条模型能力摘要（MIT 数据源）。
 * 用于发现同步写库与历史窗口估算；思考请求风格不在此对象，见厂商模板 thinkingControl。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelsDevModelInfo {

    /** 展示名 */
    private String displayName;

    /** 是否支持思考（对应 reasoning） */
    private boolean thinkingCapable;

    /** 默认是否开思考；产品固定为 false（默认可开可关、默认关） */
    private boolean thinkingDefault;

    /** 上下文窗口 token；未知时为默认值 */
    private int contextWindow;

    /** 是否来自未命中兜底（非目录真实条目） */
    private boolean fallback;
}
