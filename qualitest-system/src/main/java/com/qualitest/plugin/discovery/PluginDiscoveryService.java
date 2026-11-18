package com.qualitest.plugin.discovery;

import cn.hutool.core.util.IdUtil;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.config.PluginPaths;
import com.qualitest.plugin.config.PluginProperties;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.plugin.manifest.PluginManifestParser;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.service.ITestPluginPackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 插件包发现与登记：扫描 classpath 与本机目录中的说明书，写入或更新插件包登记表。
 * <p>
 * 同一插件 id 只保留一条有效登记；版本或整包指纹变化时覆盖更新，软删行可恢复为正常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginDiscoveryService {

    /** 插件包说明书文件名（包根目录下必须存在） */
    public static final String MANIFEST_FILE = "qualitest.plugin.jsonc";

    private final PluginManifestParser manifestParser;
    private final ITestPluginPackageService packageService;
    private final PluginProperties pluginProperties;
    private final PluginPaths pluginPaths;

    /**
     * 应用启动完成后自动执行一次发现并登记；失败只打日志，不阻断启动。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            discoverAndRegister();
        } catch (Exception e) {
            log.error("插件发现失败", e);
        }
    }

    /**
     * 扫描并登记插件包。
     * <p>
     * 先扫 classpath 里 plugins 目录下各包的说明书文件（发行物内嵌），
     * 再扫安装根目录与额外发现目录下各一级子目录中的说明书。
     *
     * @return 本次新插入或内容有更新的包数量（内容未变的不计）
     */
    @Transactional(rollbackFor = Exception.class)
    public int discoverAndRegister() throws IOException {
        int count = 0;
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:plugins/**/" + MANIFEST_FILE);
        for (Resource resource : resources) {
            if (!resource.exists()) {
                continue;
            }
            String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Path root = resolvePackageRoot(resource);
            if (upsertOne(manifestParser.parse(text), root) != null) {
                count++;
            }
        }
        for (Path base : resolveScanRoots()) {
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(base)) {
                for (Path child : stream.filter(Files::isDirectory).toList()) {
                    Path manifest = child.resolve(MANIFEST_FILE);
                    if (!Files.isRegularFile(manifest)) {
                        continue;
                    }
                    String text = Files.readString(manifest, StandardCharsets.UTF_8);
                    if (upsertOne(manifestParser.parse(text), child) != null) {
                        count++;
                    }
                }
            }
        }
        log.info("插件发现完成，更新/登记 {} 个包", count);
        return count;
    }

    /**
     * 从已解压的包目录读取说明书并登记。
     * 目录无效或缺少说明书时抛出插件异常。
     *
     * @param packageRoot 含说明书文件的包根目录
     * @return 登记后的包行；若说明书内容未变则返回库中已有行
     */
    @Transactional(rollbackFor = Exception.class)
    public TestPluginPackage registerPackageDir(Path packageRoot) throws IOException {
        if (packageRoot == null || !Files.isDirectory(packageRoot)) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "插件目录无效");
        }
        Path manifest = packageRoot.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "缺少 " + MANIFEST_FILE);
        }
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        return registerPackageDir(packageRoot, manifestParser.parse(text));
    }

    /**
     * 用调用方已解析好的说明书登记包目录。
     * 供 zip 安装在解析过说明书后直接登记，避免再读一遍磁盘文件。
     *
     * @param packageRoot 落盘后的包根目录（用于算整包指纹）
     * @param manifest    已解析的说明书对象
     * @return 登记后的包行；内容未变时查库返回现有行
     */
    @Transactional(rollbackFor = Exception.class)
    public TestPluginPackage registerPackageDir(Path packageRoot, PluginManifest manifest) {
        if (packageRoot == null || !Files.isDirectory(packageRoot)) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "插件目录无效");
        }
        if (manifest == null) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "说明书无效");
        }
        TestPluginPackage pkg = upsertOne(manifest, packageRoot);
        if (pkg != null) {
            return pkg;
        }
        // 指纹与版本均未变：upsert 未写库，按 pluginId 取出现有登记行返回给调用方
        TestPluginPackage probe = TestPluginPackage.builder().pluginId(manifest.getId()).build();
        List<TestPluginPackage> found = packageService.selectTestPluginPackageList(probe);
        if (found == null || found.isEmpty()) {
            throw new PluginException(PluginErrorCode.PACKAGE_NOT_FOUND, "登记后未找到插件包");
        }
        return found.get(0);
    }

    /**
     * 组装本机扫描根：始终包含安装根目录，再并入配置的额外发现目录（去重保序）。
     */
    private List<Path> resolveScanRoots() {
        Set<String> roots = new LinkedHashSet<>();
        roots.add(pluginPaths.resolveInstallRoot().toString());
        if (pluginProperties.getDiscoveryDirs() != null) {
            for (String dir : pluginProperties.getDiscoveryDirs()) {
                if (dir != null && !dir.isBlank()) {
                    roots.add(Path.of(dir).toAbsolutePath().normalize().toString());
                }
            }
        }
        List<Path> list = new ArrayList<>();
        for (String r : roots) {
            list.add(Path.of(r));
        }
        return list;
    }

    /**
     * 从 classpath 资源反推包根目录（说明书文件的父目录）。
     * 资源在 jar 内无法取本地文件时返回 null，此时整包指纹只含说明书文本。
     */
    private Path resolvePackageRoot(Resource resource) {
        try {
            if (resource.isFile()) {
                Path file = resource.getFile().toPath();
                return file.getParent();
            }
        } catch (Exception ignored) {
            // jar 内资源无法映射为本地目录
        }
        return null;
    }

    /**
     * 按 pluginId 插入或更新包登记。
     * 已存在且未删除、版本与整包指纹均相同则跳过写库。
     *
     * @return 新插入或已更新的行；内容未变返回 null
     */
    private TestPluginPackage upsertOne(PluginManifest manifest, Path packageRoot) {
        String hash = hashPackage(manifest.getRawJson(), packageRoot);
        TestPluginPackage probe = TestPluginPackage.builder().pluginId(manifest.getId()).build();
        List<TestPluginPackage> found = packageService.selectTestPluginPackageList(probe);
        TestPluginPackage existing = found == null || found.isEmpty() ? null : found.get(0);
        if (existing != null
                && existing.getDelStatus() != null && existing.getDelStatus() == 0
                && hash.equals(existing.getContentHash())
                && manifest.getVersion().equals(existing.getVersion())) {
            return null;
        }
        if (existing == null) {
            TestPluginPackage row = TestPluginPackage.builder()
                    .testPluginPackageId(IdUtil.getSnowflakeNextId())
                    .pluginId(manifest.getId())
                    .version(manifest.getVersion())
                    .displayName(manifest.getDisplayName())
                    .manifestJson(manifest.getRawJson())
                    .contentHash(hash)
                    .delStatus(0)
                    .build();
            row.setCreateTime(DateUtils.getNowDate());
            packageService.insertTestPluginPackage(row);
            log.info("已登记插件 {}@{}", manifest.getId(), manifest.getVersion());
            return row;
        }
        existing.setVersion(manifest.getVersion());
        existing.setDisplayName(manifest.getDisplayName());
        existing.setManifestJson(manifest.getRawJson());
        existing.setContentHash(hash);
        existing.setDelStatus(0);
        existing.setUpdateTime(DateUtils.getNowDate());
        packageService.updateTestPluginPackage(existing);
        log.info("已登记插件 {}@{}", manifest.getId(), manifest.getVersion());
        return existing;
    }

    /**
     * 计算整包内容指纹（SHA-256 十六进制）。
     * 输入为说明书原文，再加上包目录内全部常规文件的相对路径与字节内容（路径按字典序）；
     * 无本地目录时仅对说明书原文做摘要。
     */
    public String hashPackage(String manifestJson, Path packageRoot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(manifestJson.getBytes(StandardCharsets.UTF_8));
            if (packageRoot != null && Files.isDirectory(packageRoot)) {
                try (Stream<Path> walk = Files.walk(packageRoot)) {
                    walk.filter(Files::isRegularFile)
                            .sorted()
                            .forEach(p -> {
                                try {
                                    digest.update(packageRoot.relativize(p).toString().replace('\\', '/')
                                            .getBytes(StandardCharsets.UTF_8));
                                    digest.update(Files.readAllBytes(p));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("计算插件指纹失败", e);
        }
    }
}
