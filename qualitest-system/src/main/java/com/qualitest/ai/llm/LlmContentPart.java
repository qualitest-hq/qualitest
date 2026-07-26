package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

/**
 * 单条消息中的多模态内容块。
 * <p>
 * 支持纯文本与图片（URL 或 base64），由 HTTP 客户端序列化为上游所需的 content 结构。
 */
@Getter
@Builder
public class LlmContentPart {

    /** 块类型：{@code text} 或 {@code image} */
    private final String type;

    /** 文本块内容，type=text 时使用 */
    private final String text;

    /** 图片远程地址，type=image 时使用 */
    private final String imageUrl;

    /** 图片 base64 编码数据，type=image 时使用 */
    private final String imageBase64;

    /** 图片 MIME 类型，如 image/png */
    private final String imageMediaType;

    /** 构造文本块 */
    public static LlmContentPart text(String text) {
        return LlmContentPart.builder().type("text").text(text).build();
    }

    /** 构造 URL 图片块 */
    public static LlmContentPart imageUrl(String url) {
        return LlmContentPart.builder().type("image").imageUrl(url).build();
    }

    /** 构造 base64 图片块 */
    public static LlmContentPart imageBase64(String mediaType, String data) {
        return LlmContentPart.builder().type("image").imageMediaType(mediaType).imageBase64(data).build();
    }
}
