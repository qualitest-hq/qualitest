package com.qualitest.plugin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台插件宿主运行时配置，绑定配置前缀 qualitest.plugin。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qualitest.plugin")
public class PluginProperties {

    /**
     * 额外发现目录列表。
     * 每个目录下的一级子目录若含说明书文件 qualitest.plugin.jsonc，启动发现时会登记该包。
     * 不替代安装根目录；安装根目录始终参与扫描。
     */
    private List<String> discoveryDirs = new ArrayList<>();

    /**
     * 上传 zip 后的落盘根目录。
     * 非空时安装与发现都使用该路径；空字符串表示按应用上传根目录下的 plugins 子目录推导，
     * 上传根目录也为空时再回退到系统临时目录下的 qualitest/plugins。
     */
    private String installDir = "";

    /**
     * 被测端业务 CLI 可执行文件所在目录。
     * 调用插件时优先在此目录查找二进制；为空则只依赖操作系统 PATH。
     */
    private String agentBinDir = "";

    /**
     * 本机执行被测端 CLI（local-exec）的默认超时时间，单位毫秒。
     */
    private long execTimeoutMs = 120_000L;

    /**
     * 被测旁执行代理拉取制品时使用的质衡可达基址（无尾斜杠）。
     * 为空时，推送安装须由请求体提供 artifactPublicBaseUrl。
     */
    private String artifactPublicBaseUrl = "";

    /**
     * 制品短时下载 token 有效期，单位毫秒。
     */
    private long artifactTokenTtlMs = 300_000L;
}
