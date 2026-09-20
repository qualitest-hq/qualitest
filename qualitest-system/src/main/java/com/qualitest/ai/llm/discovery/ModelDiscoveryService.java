package com.qualitest.ai.llm.discovery;

import cn.hutool.core.util.IdUtil;
import com.qualitest.ai.domain.AiLlmModel;
import com.qualitest.ai.domain.AiLlmVendor;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.llm.modelsdev.ModelsDevCatalog;
import com.qualitest.ai.llm.modelsdev.ModelsDevModelInfo;
import com.qualitest.ai.llm.template.ProviderTemplate;
import com.qualitest.ai.llm.template.ProviderTemplateRegistry;
import com.qualitest.ai.mapper.AiLlmModelMapper;
import com.qualitest.ai.params.AiLlmSyncModelsParams;
import com.qualitest.ai.params.AiLlmTestConnectionParams;
import com.qualitest.ai.result.AiLlmDiscoverModelsResult;
import com.qualitest.ai.result.AiLlmDiscoveredModelItem;
import com.qualitest.ai.result.AiLlmTestConnectionResult;
import com.qualitest.ai.result.ProviderTemplateResult;
import com.qualitest.ai.service.IAiLlmVendorService;
import com.qualitest.common.core.redis.RedisCache;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型发现编排服务：拉取远端模型列表、与库内 diff、勾选同步入库，以及保存前的连通性检测。
 */
@Slf4j
@Service
public class ModelDiscoveryService {

    /** Redis 发现结果缓存键前缀，后缀为厂商 ID */
    private static final String CACHE_KEY_PREFIX = "ai:llm:discover:";

    /** 管理端 apiKey 脱敏占位符，与 {@link com.qualitest.ai.service.impl.AiLlmVendorServiceImpl} 一致 */
    private static final String MASKED_API_KEY = "******";

    @Autowired
    private ProviderTemplateRegistry providerTemplateRegistry;

    @Autowired
    private ModelsDevCatalog modelsDevCatalog;

    @Autowired
    private IAiLlmVendorService aiLlmVendorService;

    @Autowired
    private AiLlmModelMapper aiLlmModelMapper;

    @Autowired
    private RedisCache redisCache;

    /** 是否允许发现请求访问内网 baseUrl（本地 Ollama 等） */
    @Value("${ai.llm.allow-private-base-url:false}")
    private boolean allowPrivateBaseUrl;

    /** 发现结果缓存 TTL（秒） */
    @Value("${ai.llm.discover-cache-ttl:3600}")
    private int discoverCacheTtlSeconds;

    /** discoveryType → 适配器实例 */
    private Map<String, ModelDiscoveryAdapter> adapterMap;

    /**
     * 注册全部 {@link ModelDiscoveryAdapter} 实现，按 discoveryType 建立路由表。
     */
    @Autowired
    public void initAdapters(List<ModelDiscoveryAdapter> adapterList) {
        this.adapterMap = adapterList.stream()
                .collect(Collectors.toMap(ModelDiscoveryAdapter::discoveryType, Function.identity(), (a, b) -> a));
    }

    /**
     * 返回 classpath 预置的全部厂商模板，供管理端新建厂商表单使用。
     */
    public List<ProviderTemplateResult> listProviderTemplates() {
        return providerTemplateRegistry.listAll();
    }

    /**
     * 检测厂商连通性：校验 baseUrl、调用对应适配器拉取模型列表，不落库。
     *
     * @param params 模板 ID、baseUrl、apiKey 等连接参数
     * @return 成功时含 modelCount；失败时 success=false 并返回错误消息
     */
    public AiLlmTestConnectionResult testConnection(AiLlmTestConnectionParams params) {
        try {
            AiLlmVendor savedVendor = resolveSavedVendor(params.getAiLlmVendorId());
            String templateId = params.getTemplateId();
            String provider = params.getProvider();
            String discoveryType = params.getDiscoveryType();
            if (savedVendor != null) {
                if (templateId == null || templateId.isBlank()) {
                    templateId = savedVendor.getTemplateId();
                }
                if (provider == null || provider.isBlank()) {
                    provider = savedVendor.getProvider();
                }
                if (discoveryType == null || discoveryType.isBlank()) {
                    discoveryType = savedVendor.getDiscoveryType();
                }
            }
            discoveryType = resolveDiscoveryType(
                    resolveTemplate(templateId, provider, discoveryType),
                    discoveryType);
            String apiKey = resolveApiKeyForTest(params.getApiKey(), savedVendor, discoveryType);
            ProviderTemplate template = resolveTemplate(templateId, provider, discoveryType);
            String baseUrl = normalizeInputBaseUrl(params.getBaseUrl(), template);
            LlmDiscoveryUrlUtils.validateBaseUrl(baseUrl, allowPrivateBaseUrl);
            List<DiscoveredModel> models = fetchRemote(template, baseUrl, apiKey, discoveryType);
            return AiLlmTestConnectionResult.builder()
                    .success(true)
                    .message("连接成功")
                    .modelCount(models.size())
                    .build();
        } catch (LlmClientException ex) {
            return AiLlmTestConnectionResult.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .modelCount(0)
                    .build();
        }
    }

    /**
     * 拉取指定厂商的远端模型并与库内记录 diff。
     * <p>
     * refresh=false 时优先读 Redis 缓存；refresh=true 或缓存未命中时重新请求上游并写回缓存。
     *
     * @param vendorId 厂商主键
     * @param refresh  是否强制刷新远端列表
     * @return 含 NEW / EXISTING / ORPHAN 状态的模型项列表
     */
    public AiLlmDiscoverModelsResult discoverModels(Long vendorId, boolean refresh) {
        AiLlmVendor vendor = requireVendor(vendorId);
        String cacheKey = CACHE_KEY_PREFIX + vendorId;
        List<DiscoveredModel> remoteModels;
        boolean fromCache = false;
        Date fetchedAt = DateUtils.getNowDate();

        if (!refresh) {
            List<DiscoveredModel> cached = redisCache.getCacheObject(cacheKey);
            if (cached != null) {
                remoteModels = cached;
                fromCache = true;
            } else {
                remoteModels = fetchForVendor(vendor);
                redisCache.setCacheObject(cacheKey, remoteModels, discoverCacheTtlSeconds, TimeUnit.SECONDS);
            }
        } else {
            remoteModels = fetchForVendor(vendor);
            redisCache.setCacheObject(cacheKey, remoteModels, discoverCacheTtlSeconds, TimeUnit.SECONDS);
        }

        List<AiLlmDiscoveredModelItem> items = diffWithDatabase(vendor, remoteModels);
        return AiLlmDiscoverModelsResult.builder()
                .vendorId(vendorId)
                .fetchedAt(fetchedAt)
                .fromCache(fromCache)
                .models(items)
                .build();
    }

    /**
     * 将用户勾选的模型 ID 同步入库：NEW 插入行，EXISTING 更新展示名与思考能力标签；ORPHAN 跳过。
     * 同步前会强制 refresh 一次发现列表，确保状态最新。
     *
     * @return 实际插入或更新的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncModels(Long vendorId, AiLlmSyncModelsParams params) {
        AiLlmVendor vendor = requireVendor(vendorId);
        if (params == null || params.getModelIds() == null || params.getModelIds().isEmpty()) {
            return 0;
        }
        Set<String> selected = new HashSet<>(params.getModelIds());
        AiLlmDiscoverModelsResult discover = discoverModels(vendorId, true);
        Map<String, AiLlmDiscoveredModelItem> discoverMap = new HashMap<>();
        for (AiLlmDiscoveredModelItem item : discover.getModels()) {
            if (item.getModelId() != null) {
                discoverMap.put(item.getModelId(), item);
            }
        }

        int count = 0;
        for (String modelId : selected) {
            AiLlmDiscoveredModelItem item = discoverMap.get(modelId);
            if (item == null || "ORPHAN".equals(item.getStatus())) {
                continue;
            }
            ProviderTemplate template = resolveTemplate(vendor.getTemplateId(), vendor.getProvider(), vendor.getDiscoveryType());
            String modelsDevProviderId = template != null ? template.getModelsDevProviderId() : null;
            ModelsDevModelInfo info = modelsDevCatalog.lookupOrDefault(modelsDevProviderId, modelId);
            String displayName = info.getDisplayName() != null ? info.getDisplayName() : modelId;
            Integer thinkingCapable = info.isThinkingCapable() ? 1 : 0;
            Integer thinkingDefault = info.isThinkingDefault() ? 1 : 0;

            if ("NEW".equals(item.getStatus())) {
                AiLlmModel model = AiLlmModel.builder()
                        .aiLlmModelId(IdUtil.getSnowflakeNextId())
                        .aiLlmVendorId(vendorId)
                        .modelName(modelId)
                        .displayName(displayName)
                        .builtinStatus(0)
                        .enableStatus(1)
                        .thinkingCapable(thinkingCapable)
                        .thinkingDefault(thinkingDefault)
                        .sortNum(0)
                        .delStatus(0)
                        .build();
                model.setCreateTime(DateUtils.getNowDate());
                count += aiLlmModelMapper.insertAiLlmModel(model);
            } else if ("EXISTING".equals(item.getStatus()) && item.getAiLlmModelId() != null) {
                AiLlmModel existing = aiLlmModelMapper.selectAiLlmModelById(item.getAiLlmModelId());
                if (existing != null) {
                    existing.setDisplayName(displayName);
                    existing.setThinkingCapable(thinkingCapable);
                    existing.setThinkingDefault(thinkingDefault);
                    existing.setUpdateTime(DateUtils.getNowDate());
                    count += aiLlmModelMapper.updateAiLlmModel(existing);
                }
            }
        }
        return count;
    }

    /**
     * 删除指定厂商的发现结果缓存；厂商连接信息变更或删除时调用。
     */
    public void invalidateDiscoverCache(Long vendorId) {
        if (vendorId != null) {
            redisCache.deleteObject(CACHE_KEY_PREFIX + vendorId);
        }
    }

    /** 编辑场景下解析真实 apiKey：前端未改密钥时传空或脱敏占位，回退库内值 */
    private String resolveApiKeyForTest(String apiKey, AiLlmVendor savedVendor, String discoveryType) {
        String trimmed = apiKey != null ? apiKey.trim() : null;
        if (!isMaskedOrBlankApiKey(trimmed)) {
            return trimmed;
        }
        if (savedVendor != null && savedVendor.getApiKey() != null && !savedVendor.getApiKey().isBlank()) {
            return savedVendor.getApiKey().trim();
        }
        if (!requiresApiKeyForDiscovery(discoveryType)) {
            return "";
        }
        if (savedVendor != null) {
            throw new LlmClientException("数据库中未保存 API Key，请重新填写");
        }
        throw new LlmClientException("请先填写 API Key");
    }

    /** Ollama 等本地发现策略可不配置密钥 */
    private static boolean requiresApiKeyForDiscovery(String discoveryType) {
        return !"ollama_tags".equals(discoveryType) && !"none".equals(discoveryType);
    }

    private boolean isMaskedOrBlankApiKey(String apiKey) {
        return apiKey == null || apiKey.isBlank() || MASKED_API_KEY.equals(apiKey);
    }

    /** 按 ID 加载已保存厂商；不存在或未传 ID 时返回 null */
    private AiLlmVendor resolveSavedVendor(Long vendorId) {
        if (vendorId == null) {
            return null;
        }
        AiLlmVendor vendor = aiLlmVendorService.selectAiLlmVendorById(vendorId);
        if (vendor == null || (vendor.getDelStatus() != null && vendor.getDelStatus() != 0)) {
            throw new LlmClientException("厂商不存在");
        }
        return vendor;
    }

    /** 加载厂商并校验未逻辑删除 */
    private AiLlmVendor requireVendor(Long vendorId) {
        AiLlmVendor vendor = aiLlmVendorService.selectAiLlmVendorById(vendorId);
        if (vendor == null || (vendor.getDelStatus() != null && vendor.getDelStatus() != 0)) {
            throw new ServiceException("厂商不存在");
        }
        return vendor;
    }

    /** 按厂商配置拉取远端模型并合并 models.dev 元数据 */
    private List<DiscoveredModel> fetchForVendor(AiLlmVendor vendor) {
        ProviderTemplate template = resolveTemplate(vendor.getTemplateId(), vendor.getProvider(), vendor.getDiscoveryType());
        String baseUrl = vendor.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            if (template != null && template.getDefaultBaseUrl() != null) {
                baseUrl = template.getDefaultBaseUrl();
            }
        }
        LlmDiscoveryUrlUtils.validateBaseUrl(baseUrl, allowPrivateBaseUrl);
        String modelsDevProviderId = template != null ? template.getModelsDevProviderId() : null;
        return enrichWithModelsDev(fetchRemote(template, baseUrl, vendor.getApiKey(), vendor.getDiscoveryType()),
                modelsDevProviderId);
    }

    /** 按 discoveryType 选择适配器并发起上游 HTTP 发现请求 */
    private List<DiscoveredModel> fetchRemote(ProviderTemplate template, String baseUrl, String apiKey, String discoveryTypeOverride) {
        String discoveryType = resolveDiscoveryType(template, discoveryTypeOverride);
        ModelDiscoveryAdapter adapter = adapterMap.get(discoveryType);
        if (adapter == null) {
            throw new LlmClientException("不支持的发现类型：" + discoveryType);
        }
        ModelDiscoveryContext context = ModelDiscoveryContext.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .discoveryType(discoveryType)
                .discoveryPath(template != null ? template.getDiscoveryPath() : null)
                .template(template)
                .connectTimeoutMs(5000)
                .readTimeoutMs(15000)
                .build();
        return adapter.discover(context);
    }

    /**
     * 用 models.dev 补全远端模型的展示名、是否支持思考、默认是否开思考。
     * 目录未命中时按可思考、默认关闭处理。
     *
     * @param models               远端发现的原始模型列表
     * @param modelsDevProviderId  models.dev provider 键，可空
     * @return 补全后的列表
     */
    private List<DiscoveredModel> enrichWithModelsDev(List<DiscoveredModel> models, String modelsDevProviderId) {
        List<DiscoveredModel> result = new ArrayList<>();
        for (DiscoveredModel model : models) {
            ModelsDevModelInfo info = modelsDevCatalog.lookupOrDefault(modelsDevProviderId, model.getModelId());
            if (info.isFallback()) {
                log.debug("模型 {} 未命中 models.dev，按默认可思考导入", model.getModelId());
            }
            result.add(DiscoveredModel.builder()
                    .modelId(model.getModelId())
                    .ownedBy(model.getOwnedBy())
                    .remoteCreatedAt(model.getRemoteCreatedAt())
                    .displayName(info.getDisplayName() != null ? info.getDisplayName() : model.getModelId())
                    .thinkingCapable(info.isThinkingCapable() ? 1 : 0)
                    .thinkingDefault(info.isThinkingDefault() ? 1 : 0)
                    .build());
        }
        return result;
    }

    /**
     * 将远端模型与库内同厂商模型对比，生成 NEW / EXISTING / ORPHAN 状态项；
     * 推荐模型排在前面。
     */
    private List<AiLlmDiscoveredModelItem> diffWithDatabase(AiLlmVendor vendor, List<DiscoveredModel> remoteModels) {
        ProviderTemplate template = resolveTemplate(vendor.getTemplateId(), vendor.getProvider(), vendor.getDiscoveryType());
        Set<String> recommended = new HashSet<>();
        if (template != null && template.getRecommendedModels() != null) {
            recommended.addAll(template.getRecommendedModels());
        }

        AiLlmModel query = new AiLlmModel();
        query.setAiLlmVendorId(vendor.getAiLlmVendorId());
        List<AiLlmModel> dbModels = aiLlmModelMapper.selectAiLlmModelList(query).stream()
                .filter(m -> m.getDelStatus() == null || m.getDelStatus() == 0)
                .collect(Collectors.toList());
        Map<String, AiLlmModel> dbByName = new LinkedHashMap<>();
        for (AiLlmModel model : dbModels) {
            dbByName.put(model.getModelName(), model);
        }

        List<AiLlmDiscoveredModelItem> items = new ArrayList<>();
        Set<String> remoteIds = new HashSet<>();
        for (DiscoveredModel remote : remoteModels) {
            remoteIds.add(remote.getModelId());
            AiLlmModel existing = dbByName.get(remote.getModelId());
            if (existing != null) {
                items.add(AiLlmDiscoveredModelItem.builder()
                        .modelId(remote.getModelId())
                        .displayName(remote.getDisplayName())
                        .status("EXISTING")
                        .thinkingCapable(remote.getThinkingCapable())
                        .thinkingDefault(remote.getThinkingDefault())
                        .recommended(recommended.contains(remote.getModelId()))
                        .aiLlmModelId(existing.getAiLlmModelId())
                        .enableStatus(existing.getEnableStatus())
                        .builtinStatus(existing.getBuiltinStatus())
                        .build());
            } else {
                items.add(AiLlmDiscoveredModelItem.builder()
                        .modelId(remote.getModelId())
                        .displayName(remote.getDisplayName())
                        .status("NEW")
                        .thinkingCapable(remote.getThinkingCapable())
                        .thinkingDefault(remote.getThinkingDefault())
                        .recommended(recommended.contains(remote.getModelId()))
                        .build());
            }
        }
        for (AiLlmModel dbModel : dbModels) {
            if (!remoteIds.contains(dbModel.getModelName())) {
                items.add(AiLlmDiscoveredModelItem.builder()
                        .modelId(dbModel.getModelName())
                        .displayName(dbModel.getDisplayName() != null ? dbModel.getDisplayName() : dbModel.getModelName())
                        .status("ORPHAN")
                        .aiLlmModelId(dbModel.getAiLlmModelId())
                        .enableStatus(dbModel.getEnableStatus())
                        .builtinStatus(dbModel.getBuiltinStatus())
                        .build());
            }
        }
        items.sort(Comparator
                .comparing((AiLlmDiscoveredModelItem i) -> !Boolean.TRUE.equals(i.getRecommended()))
                .thenComparing(AiLlmDiscoveredModelItem::getModelId, Comparator.nullsLast(String::compareTo)));
        return items;
    }

    /** 按 templateId 查模板；缺失时用 provider + discoveryType 构造最小模板 */
    private ProviderTemplate resolveTemplate(String templateId, String provider, String discoveryType) {
        ProviderTemplate template = templateId != null ? providerTemplateRegistry.getById(templateId) : null;
        if (template == null && discoveryType != null) {
            template = new ProviderTemplate();
            template.setProvider(provider);
            template.setDiscoveryType(discoveryType);
        }
        return template;
    }

    /** 解析最终 discoveryType：优先厂商覆盖值，其次模板默认值，兜底 openai_models */
    private String resolveDiscoveryType(ProviderTemplate template, String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        if (template != null && template.getDiscoveryType() != null) {
            return template.getDiscoveryType();
        }
        return "openai_models";
    }

    /** 规范化 baseUrl：入参为空时回退模板 defaultBaseUrl */
    private String normalizeInputBaseUrl(String baseUrl, ProviderTemplate template) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return LlmDiscoveryUrlUtils.normalizeBaseUrl(baseUrl);
        }
        if (template != null && template.getDefaultBaseUrl() != null && !template.getDefaultBaseUrl().isBlank()) {
            return LlmDiscoveryUrlUtils.normalizeBaseUrl(template.getDefaultBaseUrl());
        }
        throw new LlmClientException("接口地址未配置");
    }
}
