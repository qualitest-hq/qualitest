package com.qualitest.web.controller.project;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.config.PluginPaths;
import com.qualitest.plugin.discovery.PluginDiscoveryService;
import com.qualitest.plugin.enable.PluginEnableService;
import com.qualitest.plugin.install.PluginPackageInstallService;
import com.qualitest.plugin.invoke.PluginInvokeRequest;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.plugin.invoke.PluginInvoker;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.plugin.mount.ProjectMountSupport;
import com.qualitest.plugin.push.ArtifactTokenStore;
import com.qualitest.plugin.push.SutPushService;
import com.qualitest.project.domain.TestPluginEnvBinding;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.domain.TestPluginProjectMount;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.params.TestPluginEnvBindingParams;
import com.qualitest.project.params.TestPluginPackageParams;
import com.qualitest.project.params.TestPluginProjectMountParams;
import com.qualitest.project.result.TestPluginEnvBindingResult;
import com.qualitest.project.result.TestPluginPackageResult;
import com.qualitest.project.service.ITestPluginEnvBindingService;
import com.qualitest.project.service.ITestPluginPackageService;
import com.qualitest.project.service.ITestPluginProjectMountService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台插件宿主 HTTP 接口：包登记查询、zip 安装、发现、环境启用/停用、能力调用、项目挂载、环境 UI 投影。
 */
@RestController
@RequestMapping("/project/pluginHost")
@AllArgsConstructor
public class PluginHostController extends BaseController {

    private final PluginDiscoveryService discoveryService;
    private final PluginEnableService enableService;
    private final PluginPackageInstallService installService;
    private final PluginInvoker pluginInvoker;
    private final SutPushService sutPushService;
    private final ArtifactTokenStore artifactTokenStore;
    private final PluginPaths pluginPaths;
    private final ITestPluginPackageService packageService;
    private final ITestPluginEnvBindingService bindingService;
    private final ITestPluginProjectMountService mountService;
    private final ITestProjectEnvService envService;
    private final ITestProjectMemberService memberService;

    /**
     * 查询已登记的全部插件包（含 zip 安装与本机发现目录写入的记录）。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginPackage:list')")
    @GetMapping("/packages")
    public R<List<TestPluginPackageResult>> packages() {
        return R.ok(packageService.selectTestPluginPackageResultList(new TestPluginPackageParams()));
    }

    /**
     * 上传插件 zip 到质衡本机安装目录并登记。
     * 表单字段名为 file；成功返回插件 id、版本、落盘路径与内容指纹等。
     * 仅安装质衡宿主侧包，不会向被测机写入 CLI。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginPackage:edit')")
    @Log(title = "安装插件包", businessType = BusinessType.INSERT)
    @PostMapping("/install")
    public R<Map<String, Object>> install(@RequestParam("file") MultipartFile file) {
        try {
            return R.ok(installService.installZip(file));
        } catch (PluginException e) {
            return R.fail(e.getErrorCode().getCode() + ": " + e.getMessage());
        } catch (Exception e) {
            return R.fail("安装失败: " + e.getMessage());
        }
    }

    /**
     * 查询当前 zip 安装根目录的绝对路径，供项目设置页只读展示。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginPackage:list')")
    @GetMapping("/installDir")
    public R<Map<String, String>> installDir() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("installDir", installService.resolveInstallRoot().toString());
        return R.ok(map);
    }

    /**
     * 手动再跑一遍插件发现（扫描 classpath 与本机安装/额外目录并 upsert 登记表）。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginPackage:edit')")
    @Log(title = "插件发现", businessType = BusinessType.UPDATE)
    @PostMapping("/discover")
    public R<Integer> discover() throws Exception {
        return R.ok(discoveryService.discoverAndRegister());
    }

    /**
     * 查询某环境下全部插件绑定行（含已停用），供环境管理开关回显。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginEnvBinding:list')")
    @GetMapping("/env/{envId}/bindings")
    public R<List<TestPluginEnvBindingResult>> envBindings(@PathVariable Long envId) {
        requireEnvCapability(envId, TestProjectMemberPerm.ENV_VIEW);
        TestPluginEnvBindingParams params = TestPluginEnvBindingParams.builder()
                .testProjectEnvId(envId)
                .build();
        return R.ok(bindingService.selectTestPluginEnvBindingResultList(params));
    }

    /**
     * 在指定环境启用插件：校验挂载与总闸后写入绑定；失败返回原因码与文案。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginEnvBinding:edit')")
    @Log(title = "启用插件", businessType = BusinessType.UPDATE)
    @PostMapping("/env/{envId}/enable")
    public R<TestPluginEnvBinding> enable(@PathVariable Long envId, @RequestBody EnableBody body) {
        requireEnvCapability(envId, TestProjectMemberPerm.ENV_EDIT);
        try {
            return R.ok(enableService.enable(envId, body.getPluginId(), body.getConfigJson()));
        } catch (PluginException e) {
            return R.fail(e.getErrorCode().getCode() + ": " + e.getMessage());
        }
    }

    /**
     * 在指定环境停用插件：绑定行保留，enabled 置 0。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginEnvBinding:edit')")
    @Log(title = "停用插件", businessType = BusinessType.UPDATE)
    @PostMapping("/env/{envId}/disable")
    public R<Void> disable(@PathVariable Long envId, @RequestBody EnableBody body) {
        requireEnvCapability(envId, TestProjectMemberPerm.ENV_EDIT);
        enableService.disable(envId, body.getPluginId());
        return R.ok();
    }

    /**
     * 在指定环境调用已启用插件的一项能力。
     * 返回标准输出原文，并在其为 JSON 时把字段摊平（如 saves），供列表与删除存档使用。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginEnvBinding:edit')")
    @Log(title = "调用环境插件", businessType = BusinessType.OTHER)
    @PostMapping("/env/{envId}/invoke")
    public R<Map<String, Object>> invoke(@PathVariable Long envId, @RequestBody InvokeBody body) {
        requireEnvCapability(envId, TestProjectMemberPerm.ENV_EDIT);
        try {
            PluginInvokeResult result = pluginInvoker.invoke(PluginInvokeRequest.builder()
                    .envId(envId)
                    .runId(parseRunId(body.getRunId()))
                    .pluginId(body.getPluginId())
                    .capability(body.getCapability())
                    .args(body.getArgs())
                    .source("pluginHost")
                    .build());
            return R.ok(toInvokePayload(result));
        } catch (PluginException e) {
            return R.fail(e.getErrorCode().getCode() + ": " + e.getMessage());
        }
    }

    /**
     * 读取项目对某插件的挂载 JSON；pluginId 必填。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginProjectMount:query')")
    @GetMapping("/project/{projectId}/mount")
    public R<TestPluginProjectMount> getMount(
            @PathVariable Long projectId,
            @RequestParam String pluginId) {
        requireProjectMountRead(projectId);
        if (pluginId == null || pluginId.isBlank()) {
            return R.fail("pluginId 必填");
        }
        TestPluginProjectMount mount = mountService.selectTestPluginProjectMountOne(
                TestPluginProjectMountParams.builder().testProjectId(projectId).pluginId(pluginId).build());
        return R.ok(mount);
    }

    /**
     * 列出项目下全部插件挂载行。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginProjectMount:query')")
    @GetMapping("/project/{projectId}/mounts")
    public R<List<TestPluginProjectMount>> listMounts(@PathVariable Long projectId) {
        requireProjectMountRead(projectId);
        return R.ok(mountService.selectTestPluginProjectMountList(
                TestPluginProjectMount.builder().testProjectId(projectId).build()));
    }

    /**
     * 保存项目插件挂载：按说明书 projectMount.fields 校验后写入 mountJson。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginProjectMount:edit')")
    @Log(title = "项目插件挂载", businessType = BusinessType.UPDATE)
    @PutMapping("/project/{projectId}/mount")
    public R<TestPluginProjectMount> saveMount(@PathVariable Long projectId, @RequestBody MountBody body) {
        memberService.requireCapability(projectId, TestProjectMemberPerm.ENV_EDIT);
        if (body.getPluginId() == null || body.getPluginId().isBlank()) {
            return R.fail("pluginId 必填");
        }
        String pluginId = body.getPluginId().trim();
        try {
            PluginManifest manifest = enableService.requireManifest(pluginId);
            JSONObject mountJson = body.getMountJson() != null
                    ? new JSONObject(body.getMountJson())
                    : new JSONObject();
            if (body.getBindAlias() != null) {
                mountJson.put("bindAlias", body.getBindAlias());
            }
            if (body.getStoreKeys() != null) {
                mountJson.put("storeKeys", body.getStoreKeys());
            }
            if (manifest.getProjectMount() != null) {
                mountJson = ProjectMountSupport.requireValidMount(manifest.getProjectMount(), mountJson);
            }
            TestPluginProjectMount existing = mountService.selectTestPluginProjectMountOne(
                    TestPluginProjectMountParams.builder().testProjectId(projectId).pluginId(pluginId).build());
            if (existing == null) {
                TestPluginProjectMount row = TestPluginProjectMount.builder()
                        .testProjectId(projectId)
                        .pluginId(pluginId)
                        .mountJson(mountJson.toJSONString())
                        .delStatus(0)
                        .build();
                mountService.insertTestPluginProjectMount(row);
                return R.ok(row);
            }
            existing.setMountJson(mountJson.toJSONString());
            existing.setDelStatus(0);
            mountService.updateTestPluginProjectMount(existing);
            return R.ok(existing);
        } catch (PluginException e) {
            return R.fail(e.getErrorCode().getCode() + ": " + e.getMessage());
        }
    }

    /**
     * 探活当前环境执行代理（channel=exec-agent 时）。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginEnvBinding:list')")
    @GetMapping("/env/{envId}/sut-push/ping")
    public R<Map<String, Object>> sutPushPing(@PathVariable Long envId) {
        requireEnvCapability(envId, TestProjectMemberPerm.ENV_VIEW);
        try {
            return R.ok(sutPushService.pingAgent(envId));
        } catch (PluginException e) {
            return R.fail(e.getErrorCode().getCode() + ": " + e.getMessage());
        }
    }

    /**
     * 经执行代理推送安装业务 CLI；productId 须在说明书 push.products 中。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginEnvBinding:edit')")
    @Log(title = "控制台推送安装", businessType = BusinessType.OTHER)
    @PostMapping("/env/{envId}/sut-push/install")
    public R<Map<String, Object>> sutPushInstall(@PathVariable Long envId, @RequestBody(required = false) SutPushInstallBody body) {
        requireEnvCapability(envId, TestProjectMemberPerm.ENV_EDIT);
        try {
            String productId = body == null ? null : body.getProductId();
            String publicBase = body == null ? null : body.getArtifactPublicBaseUrl();
            return R.ok(sutPushService.pushInstall(envId, productId, publicBase));
        } catch (PluginException e) {
            return R.fail(e.getErrorCode().getCode() + ": " + e.getMessage());
        }
    }

    /**
     * 经执行代理写入被测机 bind 文件。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginEnvBinding:edit')")
    @Log(title = "控制台推送 bind", businessType = BusinessType.OTHER)
    @PostMapping("/env/{envId}/sut-push/bind")
    public R<Map<String, Object>> sutPushBind(@PathVariable Long envId, @RequestBody(required = false) SutPushBindBody body) {
        requireEnvCapability(envId, TestProjectMemberPerm.ENV_EDIT);
        try {
            String productId = body == null ? null : body.getProductId();
            String alias = body == null ? null : body.getBindAlias();
            Object bindBody = body == null ? null : body.getBody();
            return R.ok(sutPushService.pushBind(envId, productId, alias, bindBody));
        } catch (PluginException e) {
            return R.fail(e.getErrorCode().getCode() + ": " + e.getMessage());
        }
    }

    /**
     * 读取已安装插件包内的 ui 静态资源（相对插件根，如 ui/env-panel.js）。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginPackage:list')")
    @GetMapping("/ui/{pluginId}/**")
    public ResponseEntity<Resource> pluginUiAsset(
            @PathVariable String pluginId,
            jakarta.servlet.http.HttpServletRequest request) {
        String prefix = "/project/pluginHost/ui/" + pluginId + "/";
        String uri = request.getRequestURI();
        int idx = uri.indexOf(prefix);
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String relative = uri.substring(idx + prefix.length());
        if (relative.isBlank() || relative.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        Path installFile = pluginPaths.resolveInstallRoot().resolve(pluginId).resolve(relative).normalize();
        if (!installFile.startsWith(pluginPaths.resolveInstallRoot().resolve(pluginId).normalize())) {
            return ResponseEntity.badRequest().build();
        }
        if (Files.isRegularFile(installFile)) {
            return ResponseEntity.ok()
                    .contentType(guessMediaType(relative))
                    .body(new FileSystemResource(installFile));
        }
        ClassPathResource cp = new ClassPathResource("plugins/" + pluginId + "/" + relative);
        if (cp.exists()) {
            return ResponseEntity.ok()
                    .contentType(guessMediaType(relative))
                    .body(cp);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 短时制品下载：供被测旁执行代理拉取；凭 path 中 token，无需用户登录。
     */
    @GetMapping("/artifact/{token}")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable String token) {
        ArtifactTokenStore.Entry entry = artifactTokenStore.getIfValid(token);
        if (entry == null || entry.getPath() == null || !Files.isRegularFile(entry.getPath())) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(entry.getPath());
        String contentType = entry.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : entry.getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + entry.getPath().getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    /**
     * 汇总当前环境插件 UI 投影：chrome.badges + env 贡献（含未启用包的登记信息供环境壳渲染）。
     */
    @PreAuthorize("@ss.hasPermi('project:testPluginEnvBinding:list')")
    @GetMapping("/env/{envId}/ui")
    public R<Map<String, Object>> envUi(@PathVariable Long envId) {
        requireEnvCapability(envId, TestProjectMemberPerm.ENV_VIEW);
        List<TestPluginEnvBindingResult> enabledBindings = bindingService.selectTestPluginEnvBindingResultList(
                TestPluginEnvBindingParams.builder().testProjectEnvId(envId).enabled(1).build());
        List<Map<String, Object>> badges = new ArrayList<>();
        for (TestPluginEnvBindingResult binding : enabledBindings) {
            if (binding.getEnabled() == null || binding.getEnabled() != 1) {
                continue;
            }
            try {
                PluginManifest manifest = enableService.requireManifest(binding.getPluginId());
                appendChromeBadges(badges, binding.getPluginId(), manifest);
            } catch (Exception ignored) {
                // 跳过坏说明书
            }
        }

        List<TestPluginPackageResult> packages = packageService.selectTestPluginPackageResultList(new TestPluginPackageParams());
        List<TestPluginEnvBindingResult> allBindings = bindingService.selectTestPluginEnvBindingResultList(
                TestPluginEnvBindingParams.builder().testProjectEnvId(envId).build());
        Map<String, TestPluginEnvBindingResult> bindingByPlugin = new LinkedHashMap<>();
        if (allBindings != null) {
            for (TestPluginEnvBindingResult b : allBindings) {
                if (b.getPluginId() != null) {
                    bindingByPlugin.put(b.getPluginId(), b);
                }
            }
        }
        List<Map<String, Object>> envContributions = new ArrayList<>();
        if (packages != null) {
            for (TestPluginPackageResult pkg : packages) {
                if (pkg == null || pkg.getPluginId() == null) {
                    continue;
                }
                try {
                    PluginManifest manifest = enableService.requireManifest(pkg.getPluginId());
                    TestPluginEnvBindingResult binding = bindingByPlugin.get(pkg.getPluginId());
                    boolean enabled = binding != null && binding.getEnabled() != null && binding.getEnabled() == 1;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("pluginId", pkg.getPluginId());
                    row.put("displayName", manifest.getDisplayName());
                    row.put("version", manifest.getVersion());
                    row.put("remarks", manifest.getRemarks());
                    row.put("enabled", enabled);
                    row.put("configJson", binding == null ? null : binding.getConfigJson());
                    row.put("configSchema", manifest.getConfigSchema());
                    row.put("requires", manifest.getRequires());
                    row.put("channelProvider", manifest.isChannelProvider());
                    row.put("projectMount", manifest.getProjectMount());
                    row.put("hooks", manifest.getHooks());
                    row.put("capabilities", manifest.getCapabilities());
                    row.put("pushProducts", manifest.getPushProducts());
                    Object envUi = manifest.getUi() == null ? null : manifest.getUi().get("env");
                    row.put("env", envUi);
                    envContributions.add(row);
                } catch (Exception ignored) {
                    // skip
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("badges", badges);
        result.put("env", envContributions);
        return R.ok(result);
    }

    /** 启用/停用插件请求体。 */
    @Data
    public static class EnableBody {
        /** 插件标识 */
        private String pluginId;
        /** 环境附加配置 JSON 字符串，可空 */
        private String configJson;
    }

    /** 保存项目插件挂载请求体。 */
    @Data
    public static class MountBody {
        /** 插件标识（必填） */
        private String pluginId;
        /** 完整挂载 JSON（优先）；也可只传常用字段 */
        private Map<String, Object> mountJson;
        /** 兼容：被测机 bind 文件别名 */
        private String bindAlias;
        /** 可选：限定存档的 storeKey 列表 */
        private List<String> storeKeys;
    }

    /** 调用环境插件能力的请求体。 */
    @Data
    public static class InvokeBody {
        /** 插件标识 */
        private String pluginId;
        /** 能力名，如 list、archive.delete、seed.apply */
        private String capability;
        /** 能力参数，如 saveId */
        private Map<String, Object> args;
        /** 测流运行 ID，JSON 数字或数字字符串均可，可空 */
        private Object runId;
    }

    /** 控制台推送安装请求体。 */
    @Data
    public static class SutPushInstallBody {
        /** 产品 id；须在说明书 push.products 中 */
        private String productId;
        /** 覆盖配置的制品公网基址；可空则用平台配置 */
        private String artifactPublicBaseUrl;
    }

    /** 控制台推送 bind 请求体。 */
    @Data
    public static class SutPushBindBody {
        /** 产品 id；空则取首个 push.products */
        private String productId;
        /** 空则用项目挂载 bindAlias */
        private String bindAlias;
        /** bind JSON 全文；空则写最小骨架 */
        private Object body;
    }

    private void appendChromeBadges(List<Map<String, Object>> badges, String pluginId, PluginManifest manifest) {
        if (manifest.getUi() == null) {
            return;
        }
        Object chrome = manifest.getUi().get("chrome");
        if (!(chrome instanceof Map<?, ?> chromeMap)) {
            return;
        }
        Object badgeList = chromeMap.get("badges");
        if (!(badgeList instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> badge) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("pluginId", pluginId);
                row.put("id", badge.get("id"));
                row.put("text", badge.get("text"));
                row.put("tone", badge.get("tone"));
                badges.add(row);
            }
        }
    }

    private static MediaType guessMediaType(String relative) {
        String lower = relative == null ? "" : relative.toLowerCase();
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            return MediaType.parseMediaType("text/javascript");
        }
        if (lower.endsWith(".css")) {
            return MediaType.parseMediaType("text/css");
        }
        if (lower.endsWith(".json") || lower.endsWith(".jsonc")) {
            return MediaType.APPLICATION_JSON;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 解析运行 ID：空则返回 null；非数字则拒绝。
     */
    private Long parseRunId(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            throw new ServiceException("runId 无效");
        }
    }

    /**
     * 按环境所属项目校验成员权限。
     */
    private void requireEnvCapability(Long envId, TestProjectMemberPerm perm) {
        TestProjectEnv env = envService.selectTestProjectEnvById(envId);
        if (env == null || env.getTestProjectId() == null) {
            throw new ServiceException("环境不存在");
        }
        memberService.requireCapability(env.getTestProjectId(), perm);
    }

    /**
     * 读项目挂载：具备查看环境，或查看接口、测试流、套件、素材任一即可。
     */
    private void requireProjectMountRead(Long projectId) {
        Set<String> caps = memberService.listCapabilities(projectId);
        if (caps.contains(TestProjectMemberPerm.ENV_VIEW.getCode())
                || caps.contains(TestProjectMemberPerm.API_VIEW.getCode())
                || caps.contains(TestProjectMemberPerm.FLOW_VIEW.getCode())
                || caps.contains(TestProjectMemberPerm.SUITE_VIEW.getCode())
                || caps.contains(TestProjectMemberPerm.ASSET_VIEW.getCode())) {
            return;
        }
        memberService.requireCapability(projectId, TestProjectMemberPerm.ENV_VIEW);
    }

    /**
     * 组装调用结果：保留 stdout，并把 stdout 中的 JSON 对象字段摊平，便于直接读取 saves。
     */
    private Map<String, Object> toInvokePayload(PluginInvokeResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", result.isOk());
        payload.put("exitCode", result.getExitCode());
        payload.put("stdout", result.getStdout());
        payload.put("stderr", result.getStderr());
        String stdout = result.getStdout();
        if (stdout == null || stdout.isBlank()) {
            return payload;
        }
        try {
            Object parsed = JSON.parse(stdout.trim());
            if (parsed instanceof JSONObject obj) {
                for (Map.Entry<String, Object> entry : obj.entrySet()) {
                    payload.putIfAbsent(entry.getKey(), entry.getValue());
                }
            } else if (parsed != null) {
                payload.put("parsed", parsed);
            }
        } catch (Exception ignored) {
            // stdout 不是 JSON 时只保留原文
        }
        return payload;
    }
}
