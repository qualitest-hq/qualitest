package com.qualitest.ai.llm.history;

import com.qualitest.ai.llm.LlmContentPart;
import com.qualitest.ai.llm.LlmMessage;
import com.qualitest.ai.llm.LlmToolCall;

import java.util.List;

/**
 * 启发式 token 估算（不依赖 tiktoken），偏保守以避免历史超窗。
 */
public final class TokenEstimator {

    /** 单条消息 role 与结构开销 */
    private static final int MESSAGE_OVERHEAD = 4;

    private TokenEstimator() {
    }

    public static int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                cjk++;
            } else {
                other++;
            }
        }
        return (cjk * 3 + 1) / 2 + (other + 3) / 4;
    }

    public static int estimateMessage(LlmMessage message) {
        if (message == null) {
            return 0;
        }
        int tokens = MESSAGE_OVERHEAD;
        tokens += estimateText(message.getContent());
        List<LlmContentPart> parts = message.getContentParts();
        if (parts != null) {
            for (LlmContentPart part : parts) {
                if (part != null && part.getText() != null) {
                    tokens += estimateText(part.getText());
                }
            }
        }
        List<LlmToolCall> toolCalls = message.getToolCalls();
        if (toolCalls != null) {
            for (LlmToolCall tc : toolCalls) {
                if (tc != null) {
                    tokens += estimateText(tc.getName());
                    tokens += estimateText(tc.getArgumentsJson());
                }
            }
        }
        return tokens;
    }

    public static int estimateMessages(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (LlmMessage message : messages) {
            total += estimateMessage(message);
        }
        return total;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}
