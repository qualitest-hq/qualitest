package com.qualitest.ai.llm.modelsdev;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.core.redis.RedisCache;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * models.dev 模型能力目录（MIT，https://models.dev）。
 * <p>
 * 运行时拉取 api.json + models.json，Redis 缓存；失败则用 classpath 快照 models.json。
 * 查找顺序：厂商 provider + modelId → 全局精确 id → id 末段 → family。
 * 未命中返回兜底：可思考、默认关、展示名=modelId、context=32768。
 */
@Slf4j
@Component
public class ModelsDevCatalog {

    public static final int DEFAULT_CONTEXT_WINDOW = 32768;

    private static final String CACHE_KEY_API = "ai:llm:models-dev:api";
    private static final String CACHE_KEY_MODELS = "ai:llm:models-dev:models";
    private static final String SNAPSHOT_MODELS = "ai/llm/models-dev/models.json";

    @Value("${ai.llm.models-dev.enabled:true}")
    private boolean enabled;

    @Value("${ai.llm.models-dev.api-url:https://models.dev/api.json}")
    private String apiUrl;

    @Value("${ai.llm.models-dev.models-url:https://models.dev/models.json}")
    private String modelsUrl;

    @Value("${ai.llm.models-dev.cache-ttl-seconds:86400}")
    private int cacheTtlSeconds;

    @Autowired(required = false)
    private RedisCache redisCache;

    /** providerId -> (modelId -> info) */
    private final AtomicReference<Map<String, Map<String, ModelsDevModelInfo>>> byProvider =
            new AtomicReference<>(Collections.emptyMap());

    /** 全量 id / 末段 id → info */
    private final AtomicReference<Map<String, ModelsDevModelInfo>> byId =
            new AtomicReference<>(Collections.emptyMap());

    /** family → 候选列表（取上下文最大且可思考优先） */
    private final AtomicReference<Map<String, List<ModelsDevModelInfo>>> byFamily =
            new AtomicReference<>(Collections.emptyMap());

    private final Object loadLock = new Object();
    private volatile boolean loaded;

    @PostConstruct
    public void init() {
        try {
            ensureLoaded();
        } catch (Exception ex) {
            log.warn("models.dev 启动预热失败，将在首次查询时重试: {}", ex.getMessage());
        }
    }

    /**
     * 按厂商与上游 modelId 查找；未命中返回兜底条目（非 null）。
     *
     * @param modelsDevProviderId models.dev provider 键，可空
     * @param modelId             上游模型 ID
     */
    public ModelsDevModelInfo lookupOrDefault(String modelsDevProviderId, String modelId) {
        ModelsDevModelInfo found = lookup(modelsDevProviderId, modelId);
        if (found != null) {
            return found;
        }
        return fallback(modelId);
    }

    /**
     * 查找命中条目；未命中返回 null。
     */
    public ModelsDevModelInfo lookup(String modelsDevProviderId, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        String id = modelId.trim();
        ensureLoaded();

        if (modelsDevProviderId != null && !modelsDevProviderId.isBlank()) {
            Map<String, ModelsDevModelInfo> providerModels = byProvider.get().get(modelsDevProviderId.trim());
            if (providerModels != null) {
                ModelsDevModelInfo hit = providerModels.get(id);
                if (hit != null) {
                    return hit;
                }
            }
        }

        Map<String, ModelsDevModelInfo> idIndex = byId.get();
        ModelsDevModelInfo exact = idIndex.get(id);
        if (exact != null) {
            return exact;
        }
        int slash = id.lastIndexOf('/');
        if (slash >= 0 && slash < id.length() - 1) {
            ModelsDevModelInfo byTail = idIndex.get(id.substring(slash + 1));
            if (byTail != null) {
                return byTail;
            }
        }

        List<ModelsDevModelInfo> familyHits = byFamily.get().get(id);
        if (familyHits != null && !familyHits.isEmpty()) {
            return pickBest(familyHits);
        }
        return null;
    }

    /** 未命中时的产品兜底 */
    public static ModelsDevModelInfo fallback(String modelId) {
        String name = modelId == null || modelId.isBlank() ? "unknown" : modelId.trim();
        return ModelsDevModelInfo.builder()
                .displayName(name)
                .thinkingCapable(true)
                .thinkingDefault(false)
                .contextWindow(DEFAULT_CONTEXT_WINDOW)
                .fallback(true)
                .build();
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (loadLock) {
            if (loaded) {
                return;
            }
            reloadIndexes();
            loaded = true;
        }
    }

    /** 强制刷新（管理端或定时可调用） */
    public void refresh() {
        synchronized (loadLock) {
            if (redisCache != null) {
                redisCache.deleteObject(CACHE_KEY_API);
                redisCache.deleteObject(CACHE_KEY_MODELS);
            }
            reloadIndexes();
            loaded = true;
        }
    }

    private void reloadIndexes() {
        Map<String, Map<String, ModelsDevModelInfo>> providerMap = new HashMap<>();
        Map<String, ModelsDevModelInfo> idMap = new HashMap<>();
        Map<String, List<ModelsDevModelInfo>> familyMap = new HashMap<>();

        JSONObject modelsJson = loadModelsJson();
        if (modelsJson != null) {
            indexModelsJson(modelsJson, idMap, familyMap);
        }

        JSONObject apiJson = loadApiJson();
        if (apiJson != null) {
            indexApiJson(apiJson, providerMap, idMap, familyMap);
        }

        byProvider.set(Collections.unmodifiableMap(providerMap));
        byId.set(Collections.unmodifiableMap(idMap));
        byFamily.set(freezeFamily(familyMap));
        log.info("models.dev 索引就绪：provider={}，id={}，family={}",
                providerMap.size(), idMap.size(), familyMap.size());
    }

    private JSONObject loadModelsJson() {
        if (enabled) {
            String remote = fetchCached(CACHE_KEY_MODELS, modelsUrl);
            if (remote != null) {
                try {
                    return JSON.parseObject(remote);
                } catch (Exception ex) {
                    log.warn("解析 models.dev models.json 失败: {}", ex.getMessage());
                }
            }
        }
        return readClasspathJson(SNAPSHOT_MODELS);
    }

    private JSONObject loadApiJson() {
        if (!enabled) {
            return null;
        }
        String remote = fetchCached(CACHE_KEY_API, apiUrl);
        if (remote == null) {
            return null;
        }
        try {
            return JSON.parseObject(remote);
        } catch (Exception ex) {
            log.warn("解析 models.dev api.json 失败: {}", ex.getMessage());
            return null;
        }
    }

    private String fetchCached(String cacheKey, String url) {
        if (redisCache != null) {
            String cached = redisCache.getCacheObject(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
        }
        String body = httpGet(url);
        if (body != null && redisCache != null) {
            redisCache.setCacheObject(cacheKey, body, cacheTtlSeconds, TimeUnit.SECONDS);
        }
        return body;
    }

    private String httpGet(String url) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("拉取 models.dev 失败 HTTP {} url={}", response.code(), url);
                return null;
            }
            String body = response.body().string();
            return body.isBlank() ? null : body;
        } catch (IOException ex) {
            log.warn("拉取 models.dev 网络失败 url={} err={}", url, ex.getMessage());
            return null;
        }
    }

    private static JSONObject readClasspathJson(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JSON.parseObject(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private static void indexModelsJson(JSONObject modelsJson,
                                        Map<String, ModelsDevModelInfo> idMap,
                                        Map<String, List<ModelsDevModelInfo>> familyMap) {
        for (String key : modelsJson.keySet()) {
            JSONObject entry = modelsJson.getJSONObject(key);
            if (entry == null) {
                continue;
            }
            ModelsDevModelInfo info = toInfo(entry, key);
            putId(idMap, key, info);
            String shortId = shortId(key);
            if (shortId != null) {
                putId(idMap, shortId, info);
            }
            String family = entry.getString("family");
            if (family != null && !family.isBlank()) {
                familyMap.computeIfAbsent(family.trim(), k -> new ArrayList<>()).add(info);
            }
        }
    }

    private static void indexApiJson(JSONObject apiJson,
                                     Map<String, Map<String, ModelsDevModelInfo>> providerMap,
                                     Map<String, ModelsDevModelInfo> idMap,
                                     Map<String, List<ModelsDevModelInfo>> familyMap) {
        for (String providerId : apiJson.keySet()) {
            JSONObject provider = apiJson.getJSONObject(providerId);
            if (provider == null) {
                continue;
            }
            JSONObject models = provider.getJSONObject("models");
            if (models == null) {
                continue;
            }
            Map<String, ModelsDevModelInfo> modelMap = providerMap.computeIfAbsent(providerId, k -> new HashMap<>());
            for (String modelId : models.keySet()) {
                JSONObject entry = models.getJSONObject(modelId);
                if (entry == null) {
                    continue;
                }
                ModelsDevModelInfo info = toInfo(entry, modelId);
                modelMap.put(modelId, info);
                putId(idMap, modelId, info);
                String fullId = entry.getString("id");
                if (fullId != null && !fullId.isBlank()) {
                    putId(idMap, fullId, info);
                    String shortId = shortId(fullId);
                    if (shortId != null) {
                        putId(idMap, shortId, info);
                    }
                }
                String family = entry.getString("family");
                if (family != null && !family.isBlank()) {
                    familyMap.computeIfAbsent(family.trim(), k -> new ArrayList<>()).add(info);
                }
            }
        }
    }

    private static ModelsDevModelInfo toInfo(JSONObject entry, String fallbackName) {
        String name = entry.getString("name");
        if (name == null || name.isBlank()) {
            name = fallbackName;
        }
        boolean reasoning = Boolean.TRUE.equals(entry.getBoolean("reasoning"));
        int context = DEFAULT_CONTEXT_WINDOW;
        JSONObject limit = entry.getJSONObject("limit");
        if (limit != null) {
            Integer ctx = limit.getInteger("context");
            if (ctx != null && ctx > 0) {
                context = ctx;
            }
        }
        return ModelsDevModelInfo.builder()
                .displayName(name)
                .thinkingCapable(reasoning)
                .thinkingDefault(false)
                .contextWindow(context)
                .fallback(false)
                .build();
    }

    private static void putId(Map<String, ModelsDevModelInfo> idMap, String key, ModelsDevModelInfo info) {
        if (key == null || key.isBlank()) {
            return;
        }
        ModelsDevModelInfo existing = idMap.get(key);
        if (existing == null || prefer(info, existing)) {
            idMap.put(key, info);
        }
    }

    private static boolean prefer(ModelsDevModelInfo a, ModelsDevModelInfo b) {
        if (a.isThinkingCapable() != b.isThinkingCapable()) {
            return a.isThinkingCapable();
        }
        return a.getContextWindow() > b.getContextWindow();
    }

    private static ModelsDevModelInfo pickBest(List<ModelsDevModelInfo> list) {
        return list.stream()
                .max(Comparator
                        .comparing(ModelsDevModelInfo::isThinkingCapable)
                        .thenComparingInt(ModelsDevModelInfo::getContextWindow))
                .orElse(list.get(0));
    }

    private static String shortId(String id) {
        int slash = id.lastIndexOf('/');
        if (slash >= 0 && slash < id.length() - 1) {
            return id.substring(slash + 1);
        }
        return null;
    }

    private static Map<String, List<ModelsDevModelInfo>> freezeFamily(Map<String, List<ModelsDevModelInfo>> familyMap) {
        Map<String, List<ModelsDevModelInfo>> frozen = new HashMap<>();
        for (Map.Entry<String, List<ModelsDevModelInfo>> e : familyMap.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }
}
