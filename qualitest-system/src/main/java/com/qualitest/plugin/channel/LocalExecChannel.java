package com.qualitest.plugin.channel;

import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.config.PluginProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本机执行被测端业务 CLI（进程通道）。
 * 将配置写入临时 JSON，以 --config 传给子进程；超时则强杀并抛异常。
 */
@Component
@RequiredArgsConstructor
public class LocalExecChannel {

    private final PluginProperties pluginProperties;

    /**
     * 启动一次 CLI 并等待结束。
     *
     * @param bin       可执行文件名或绝对路径（会经 agentBinDir 解析）
     * @param args      命令参数（不含 --config）
     * @param configText 非空时写入临时文件并追加 --config 路径
     * @param timeoutMs 等待上限；小于等于 0 时用宿主默认超时
     */
    public ExecResult exec(String bin, List<String> args, String configText, long timeoutMs) {
        Path configFile = null;
        try {
            List<String> command = new ArrayList<>();
            command.add(resolveBin(bin));
            if (args != null) {
                command.addAll(args);
            }
            if (configText != null) {
                configFile = Files.createTempFile("qt-plugin-config-", ".json");
                Files.writeString(configFile, configText, StandardCharsets.UTF_8);
                command.add("--config");
                command.add(configFile.toAbsolutePath().toString());
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            long effectiveTimeout = timeoutMs > 0 ? timeoutMs : pluginProperties.getExecTimeoutMs();
            boolean finished = process.waitFor(effectiveTimeout, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new PluginException(PluginErrorCode.INVOKE_FAILED, "CLI 执行超时: " + bin);
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return ExecResult.builder()
                    .exitCode(process.exitValue())
                    .stdout(stdout)
                    .stderr(stderr)
                    .build();
        } catch (PluginException e) {
            throw e;
        } catch (IOException e) {
            throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE, "无法启动 CLI: " + bin + " — " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PluginException(PluginErrorCode.INVOKE_FAILED, "CLI 执行被中断", e);
        } finally {
            if (configFile != null) {
                try {
                    Files.deleteIfExists(configFile);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    /**
     * 解析可执行路径：若配置了 agentBinDir，优先取该目录下的同名文件或 .exe；
     * 找不到则原样返回 bin，交给操作系统 PATH 解析。
     */
    private String resolveBin(String bin) {
        if (bin == null || bin.isBlank()) {
            throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE, "cli.bin 为空");
        }
        String dir = pluginProperties.getAgentBinDir();
        if (dir != null && !dir.isBlank()) {
            Path candidate = Path.of(dir, bin);
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
            Path exe = Path.of(dir, bin + ".exe");
            if (Files.isRegularFile(exe)) {
                return exe.toAbsolutePath().toString();
            }
        }
        return bin;
    }

    /** 单次进程执行的退出码与标准输出/错误。 */
    @Getter
    @Builder
    public static class ExecResult {
        /** 进程退出码，0 表示成功 */
        private final int exitCode;
        /** 标准输出全文 */
        private final String stdout;
        /** 标准错误全文 */
        private final String stderr;
    }
}
