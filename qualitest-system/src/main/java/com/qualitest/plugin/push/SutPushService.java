package com.qualitest.plugin.push;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.channel.EnvExecChannelSupport;
import com.qualitest.plugin.channel.ExecAgentChannel;
import com.qualitest.plugin.config.PluginPaths;
import com.qualitest.plugin.config.PluginProperties;
import com.qualitest.plugin.enable.PluginEnableService;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.domain.TestPluginProjectMount;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.params.TestPluginProjectMountParams;
import com.qualitest.project.service.ITestPluginPackageService;
import com.qualitest.project.service.ITestPluginProjectMountService;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web 控制台推送编排：按说明书 push.products 解析制品，经执行代理安装业务 CLI、写 bind。
 */
@Service
@RequiredArgsConstructor
public class SutPushService {

    private final EnvExecChannelSupport channelSupport;
    private final ExecAgentChannel execAgentChannel;
    private final PluginProperties pluginProperties;
    private final PluginPaths pluginPaths;
    private final ArtifactTokenStore artifactTokenStore;
    private final ITestProjectEnvService envService;
    private final ITestPluginPackageService packageService;
    private final ITestPluginProjectMountService mountService;
    private final PluginEnableService enableService;

    /**
     * 经执行代理推送安装被测端产品二进制；productId 须出现在某已登记包的 push.products。
     */
    public Map<String, Object> pushInstall(Long envId, String productId, String publicBaseUrl) {
        if (productId == null || productId.isBlank()) {
            throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "productId 必填");
        }
        EnvExecChannelSupport.ChannelEndpoint ep = channelSupport.requireExecAgent(envId);
        execAgentChannel.ping(ep.getBaseUrl(), ep.getToken());

        ProductArtifact artifact = resolveArtifact(productId.trim());
        String base = resolvePublicBase(publicBaseUrl);
        String token = artifactTokenStore.issue(artifact.path(), "application/octet-stream",
                pluginProperties.getArtifactTokenTtlMs());
        String artifactUrl = base + "/project/pluginHost/artifact/" + token;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId.trim());
        payload.put("version", artifact.version());
        payload.put("artifactUrl", artifactUrl);
        payload.put("sha256", artifact.sha256());

        JSONObject resp = execAgentChannel.install(ep.getBaseUrl(), ep.getToken(), payload);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("productId", productId.trim());
        out.put("version", artifact.version());
        out.put("sha256", artifact.sha256());
        out.put("agent", resp);
        return out;
    }

    /**
     * 推送写 bind：body 由调用方提供；缺省时用项目挂载 alias + 最小骨架。
     */
    public Map<String, Object> pushBind(Long envId, String productId, String bindAlias, Object body) {
        EnvExecChannelSupport.ChannelEndpoint ep = channelSupport.requireExecAgent(envId);
        execAgentChannel.ping(ep.getBaseUrl(), ep.getToken());

        TestProjectEnv env = envService.selectTestProjectEnvById(envId);
        if (env == null) {
            throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "环境不存在");
        }
        String alias = bindAlias;
        if (alias == null || alias.isBlank()) {
            alias = loadDefaultBindAlias(env.getTestProjectId());
        }
        alias = alias.trim();
        if (alias.isBlank()) {
            throw new PluginException(PluginErrorCode.PROJECT_MOUNT_REQUIRED, "请先在项目设置挂载 bindAlias");
        }

        Object bindBody = body;
        if (bindBody == null) {
            JSONObject skeleton = new JSONObject();
            skeleton.put("bindAlias", alias);
            skeleton.put("projectId", String.valueOf(env.getTestProjectId()));
            skeleton.put("targets", new JSONObject());
            bindBody = skeleton;
        }

        String resolvedProduct = productId == null || productId.isBlank()
                ? resolveDefaultProductId()
                : productId.trim();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", resolvedProduct);
        payload.put("bindAlias", alias);
        payload.put("body", bindBody);

        JSONObject resp = execAgentChannel.bindWrite(ep.getBaseUrl(), ep.getToken(), payload);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("bindAlias", alias);
        out.put("agent", resp);
        return out;
    }

    /**
     * 探活当前环境执行代理（供 UI）。
     */
    public Map<String, Object> pingAgent(Long envId) {
        EnvExecChannelSupport.ChannelEndpoint ep = channelSupport.resolve(envId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", ep.getKind().getWire());
        out.put("baseUrl", ep.getBaseUrl());
        out.put("pluginId", ep.getPluginId());
        if (ep.getKind() != com.qualitest.plugin.channel.ExecChannelKind.EXEC_AGENT) {
            out.put("ok", false);
            out.put("reachable", false);
            out.put("message", "当前为 local-exec（未启用执行通道插件）");
            return out;
        }
        ExecAgentChannel.PingResult ping = execAgentChannel.ping(ep.getBaseUrl(), ep.getToken());
        out.put("ok", true);
        out.put("reachable", true);
        out.put("version", ping.getVersion());
        out.put("hostname", ping.getHostname());
        return out;
    }

    private String loadDefaultBindAlias(Long projectId) {
        List<TestPluginPackage> packages = packageService.selectTestPluginPackageList(new TestPluginPackage());
        if (packages == null) {
            return null;
        }
        for (TestPluginPackage pkg : packages) {
            if (pkg == null || pkg.getDelStatus() != null && pkg.getDelStatus() != 0) {
                continue;
            }
            PluginManifest manifest;
            try {
                manifest = enableService.requireManifest(pkg.getPluginId());
            } catch (Exception e) {
                continue;
            }
            if (manifest.getProjectMount() == null || manifest.getProjectMount().getFields().isEmpty()) {
                continue;
            }
            boolean hasBindAlias = manifest.getProjectMount().getFields().stream()
                    .anyMatch(f -> "bindAlias".equals(f.getName()));
            if (!hasBindAlias) {
                continue;
            }
            TestPluginProjectMount mount = mountService.selectTestPluginProjectMountOne(
                    TestPluginProjectMountParams.builder()
                            .testProjectId(projectId)
                            .pluginId(pkg.getPluginId())
                            .build());
            if (mount == null || mount.getMountJson() == null) {
                continue;
            }
            JSONObject obj = JSON.parseObject(mount.getMountJson());
            if (obj == null) {
                continue;
            }
            String alias = obj.getString("bindAlias");
            if (alias != null && !alias.isBlank()) {
                return alias.trim();
            }
        }
        return null;
    }

    private String resolveDefaultProductId() {
        List<TestPluginPackage> packages = packageService.selectTestPluginPackageList(new TestPluginPackage());
        if (packages != null) {
            for (TestPluginPackage pkg : packages) {
                try {
                    PluginManifest m = enableService.requireManifest(pkg.getPluginId());
                    if (!m.getPushProducts().isEmpty()) {
                        return m.getPushProducts().get(0).getProductId();
                    }
                } catch (Exception ignored) {
                    // skip
                }
            }
        }
        throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "无可用 push.products，请指定 productId");
    }

    private String resolvePublicBase(String override) {
        String base = override != null && !override.isBlank()
                ? override.trim()
                : pluginProperties.getArtifactPublicBaseUrl();
        if (base == null || base.isBlank()) {
            throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID,
                    "未配置 qualitest.plugin.artifact-public-base-url，且请求未提供 artifactPublicBaseUrl");
        }
        return base.replaceAll("/+$", "");
    }

    private ProductArtifact resolveArtifact(String productId) {
        List<TestPluginPackage> packages = packageService.selectTestPluginPackageList(new TestPluginPackage());
        if (packages == null || packages.isEmpty()) {
            throw new PluginException(PluginErrorCode.PACKAGE_NOT_FOUND, "无已登记插件包");
        }
        for (TestPluginPackage pkg : packages) {
            if (pkg == null || (pkg.getDelStatus() != null && pkg.getDelStatus() != 0)) {
                continue;
            }
            PluginManifest manifest;
            try {
                manifest = enableService.requireManifest(pkg.getPluginId());
            } catch (Exception e) {
                continue;
            }
            for (PluginManifest.PushProduct product : manifest.getPushProducts()) {
                if (!productId.equals(product.getProductId())) {
                    continue;
                }
                Path bin = findProductBinary(pkg.getPluginId(), product);
                String sha;
                try {
                    sha = sha256Hex(bin);
                } catch (IOException e) {
                    throw new PluginException(PluginErrorCode.INVOKE_FAILED, "计算制品哈希失败: " + e.getMessage(), e);
                }
                String version = pkg.getVersion() != null ? pkg.getVersion() : "0.1.0";
                return new ProductArtifact(bin, sha, version);
            }
        }
        throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "不支持的 productId: " + productId);
    }

    private Path findProductBinary(String pluginId, PluginManifest.PushProduct product) {
        String dir = pluginProperties.getAgentBinDir();
        String winName = product.getArtifactWin();
        String unixName = product.getArtifact();
        if (dir != null && !dir.isBlank()) {
            if (unixName != null) {
                Path simple = Path.of(dir, Path.of(unixName).getFileName().toString());
                if (Files.isRegularFile(simple)) {
                    return simple;
                }
            }
            if (winName != null) {
                Path simpleWin = Path.of(dir, Path.of(winName).getFileName().toString());
                if (Files.isRegularFile(simpleWin)) {
                    return simpleWin;
                }
            }
        }
        Path installRoot = pluginPaths.resolveInstallRoot().resolve(pluginId);
        Path[] candidates = {
                unixName == null ? null : installRoot.resolve(unixName),
                winName == null ? null : installRoot.resolve(winName),
                unixName == null ? null : installRoot.resolve(Path.of(unixName).getFileName().toString()),
                winName == null ? null : installRoot.resolve(Path.of(winName).getFileName().toString()),
        };
        for (Path c : candidates) {
            if (c != null && Files.isRegularFile(c)) {
                return c;
            }
        }
        throw new PluginException(PluginErrorCode.PACKAGE_NOT_FOUND,
                "未找到制品 " + productIdLabel(product) + "：请配置 qualitest.plugin.agent-bin-dir 或放入 "
                        + installRoot);
    }

    private static String productIdLabel(PluginManifest.PushProduct product) {
        return product.getProductId() + " (" + product.getArtifact() + ")";
    }

    private static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException(e);
        }
    }

    private record ProductArtifact(Path path, String sha256, String version) {
    }
}
