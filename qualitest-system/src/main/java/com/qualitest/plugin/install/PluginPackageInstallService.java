package com.qualitest.plugin.install;

import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.config.PluginPaths;
import com.qualitest.plugin.discovery.PluginDiscoveryService;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.plugin.manifest.PluginManifestParser;
import com.qualitest.project.domain.TestPluginPackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 质衡本机插件包安装：接收上传的 zip，解压到安装根目录，并写入插件包登记表。
 * <p>
 * 只负责质衡服务器本机落盘与登记，不向被测机安装 CLI、不写被测机 bind 文件。
 * 同 pluginId 再次安装时覆盖原目录内容并重新登记。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginPackageInstallService {

    /** 允许上传的 zip 最大字节数（10MB） */
    private static final long MAX_ZIP_BYTES = 10L * 1024 * 1024;
    /** 同上限，用于错误提示中的兆字节数 */
    private static final long MAX_ZIP_MB = MAX_ZIP_BYTES / (1024 * 1024);

    private final PluginPaths pluginPaths;
    private final PluginManifestParser manifestParser;
    private final PluginDiscoveryService discoveryService;

    /**
     * 返回当前用于 zip 落盘的安装根目录绝对路径（供界面展示）。
     */
    public Path resolveInstallRoot() {
        return pluginPaths.resolveInstallRoot();
    }

    /**
     * 安装上传的插件 zip。
     * <p>
     * 流程：校验扩展名与大小 → 解压到临时目录 → 定位含说明书的包根 → 解析说明书取插件 id →
     * 复制到安装根下以插件 id 命名的子目录（已存在则先删）→ 按说明书登记到库表 → 清理临时目录。
     *
     * @param file 表单字段名为 file 的 multipart 文件，须为 zip
     * @return 含插件 id、版本、展示名、落盘路径、登记主键、内容指纹的结果
     */
    public Map<String, Object> installZip(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "请选择插件 zip");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".zip")) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "仅支持 .zip 插件包");
        }
        if (file.getSize() > MAX_ZIP_BYTES) {
            throw new PluginException(PluginErrorCode.MANIFEST_INVALID,
                    "插件包过大（上限 " + MAX_ZIP_MB + "MB）");
        }

        Path tempRoot = Files.createTempDirectory("qt-plugin-zip-");
        try {
            unzipSafely(file.getInputStream(), tempRoot);
            Path packageRoot = locatePackageRoot(tempRoot);
            Path manifestPath = packageRoot.resolve(PluginDiscoveryService.MANIFEST_FILE);
            String manifestText = Files.readString(manifestPath, StandardCharsets.UTF_8);
            PluginManifest manifest = manifestParser.parse(manifestText);

            Path installRoot = pluginPaths.resolveInstallRoot();
            Files.createDirectories(installRoot);
            Path target = installRoot.resolve(manifest.getId()).normalize();
            // 拒绝插件 id 中含 .. 等导致跳出安装根的路径
            if (!target.startsWith(installRoot)) {
                throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "非法插件 id 路径");
            }
            if (Files.exists(target)) {
                deleteRecursive(target);
            }
            Files.createDirectories(target.getParent());
            copyDirectory(packageRoot, target);

            // 传入已解析说明书，登记侧不再重复读盘解析
            TestPluginPackage pkg = discoveryService.registerPackageDir(target, manifest);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pluginId", manifest.getId());
            result.put("version", manifest.getVersion());
            result.put("displayName", manifest.getDisplayName());
            result.put("installPath", target.toString());
            result.put("testPluginPackageId", String.valueOf(pkg.getTestPluginPackageId()));
            result.put("contentHash", pkg.getContentHash());
            log.info("已安装插件包 {}@{} -> {}", manifest.getId(), manifest.getVersion(), target);
            return result;
        } finally {
            deleteRecursive(tempRoot);
        }
    }

    /**
     * 在解压结果中定位插件包根目录：优先解压根下直接有说明书，否则取一级子目录中含说明书的那一个。
     */
    private Path locatePackageRoot(Path extractedRoot) throws IOException {
        Path direct = extractedRoot.resolve(PluginDiscoveryService.MANIFEST_FILE);
        if (Files.isRegularFile(direct)) {
            return extractedRoot;
        }
        try (var stream = Files.list(extractedRoot)) {
            for (Path child : stream.filter(Files::isDirectory).toList()) {
                Path nested = child.resolve(PluginDiscoveryService.MANIFEST_FILE);
                if (Files.isRegularFile(nested)) {
                    return child;
                }
            }
        }
        throw new PluginException(PluginErrorCode.MANIFEST_INVALID,
                "zip 根目录或一级子目录须含 " + PluginDiscoveryService.MANIFEST_FILE);
    }

    /**
     * 安全解压 zip：每个条目归一化后必须落在目标目录内，防止路径穿越写出目录外。
     */
    private void unzipSafely(InputStream in, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new PluginException(PluginErrorCode.MANIFEST_INVALID, "zip 含非法路径");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /** 递归复制目录树到目标路径（覆盖同名文件）。 */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(dir);
                Files.createDirectories(target.resolve(rel));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(file);
                Files.copy(file, target.resolve(rel), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 递归删除目录或文件；不存在则忽略。 */
    private void deleteRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
