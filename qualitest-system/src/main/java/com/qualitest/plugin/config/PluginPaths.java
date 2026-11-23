package com.qualitest.plugin.config;

import com.qualitest.common.config.QualitestConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 解析平台插件在质衡本机上的目录路径。
 * <p>
 * 上传 zip 安装时的落盘根目录、启动后扫描已安装包时的根目录，都走这里的同一套规则，
 * 避免两处各自计算导致装到 A 目录、发现扫 B 目录。
 */
@Component
@RequiredArgsConstructor
public class PluginPaths {

    private final PluginProperties pluginProperties;

    /**
     * 解析插件 zip 安装根目录（绝对路径、已规范化）。
     * 优先使用配置项 qualitest.plugin.install-dir；
     * 未配置则取应用上传根目录再拼 plugins 子目录；
     * 上传根目录也为空时，回退到系统临时目录下的 qualitest/plugins。
     * 每个插件包最终落在本根目录下以插件 id 命名的子目录中。
     */
    public Path resolveInstallRoot() {
        String configured = pluginProperties.getInstallDir();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String profile = QualitestConfig.getProfile();
        if (profile == null || profile.isBlank()) {
            profile = System.getProperty("java.io.tmpdir") + "/qualitest";
        }
        return Path.of(profile, "plugins").toAbsolutePath().normalize();
    }
}
