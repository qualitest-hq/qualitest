package com.qualitest.plugin.push;

import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短时制品下载凭证：代理凭 token 从 Host 拉取本机二进制。
 */
@Component
public class ArtifactTokenStore {

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();

    /**
     * 登记一次可下载文件，返回 token。
     */
    public String issue(Path file, String contentType, long ttlMs) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long exp = System.currentTimeMillis() + Math.max(ttlMs, 5_000L);
        tokens.put(token, Entry.builder().path(file).contentType(contentType).expiresAtMs(exp).build());
        return token;
    }

    /**
     * 消费或只读取：过期删除并返回 null。
     */
    public Entry getIfValid(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Entry e = tokens.get(token);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.getExpiresAtMs()) {
            tokens.remove(token);
            return null;
        }
        return e;
    }

    /** 主动作废（下载一次后可调用）。 */
    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /** 清理过期项（可选调用）。 */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entry> e : tokens.entrySet()) {
            if (e.getValue().getExpiresAtMs() < now) {
                tokens.remove(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * 短时下载令牌对应的制品元数据。
     */
    @Getter
    @Builder
    public static class Entry {
        /** 制品本地路径 */
        private final Path path;
        /** 下载响应 Content-Type */
        private final String contentType;
        /** 过期时刻（毫秒时间戳） */
        private final long expiresAtMs;
    }
}
