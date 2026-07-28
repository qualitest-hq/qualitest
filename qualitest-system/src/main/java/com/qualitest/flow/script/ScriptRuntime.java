package com.qualitest.flow.script;

import com.qualitest.api.script.ApiScriptContext;
import com.qualitest.api.script.ApiScriptExecutionResult;
import com.qualitest.api.script.ApiScriptHost;
import com.qualitest.api.script.ApiScriptPhase;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.IOAccess;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 受限脚本运行时：在 GraalVM 沙箱中执行 JavaScript / Python 源码。
 * <p>
 * 禁止 IO、线程、本地访问；通过 {@link ScriptHostContext} 暴露只读 env/asset/HTTP 与可写 flow。
 * 超时与并发由 {@link ScriptConstants} 控制。
 */
@Component
public class ScriptRuntime {

    private static final Semaphore CONCURRENCY = new Semaphore(ScriptConstants.MAX_CONCURRENT_SCRIPTS);

    /** 进程内共享 Engine，避免每次新建 Context 重复加载语言（尤其 Python 冷启动很慢） */
    private static final Engine SHARED_ENGINE = Engine.create();

    private final IDebugHttpForwardService forwardService;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "flow-script-runtime");
        t.setDaemon(true);
        return t;
    });

    public ScriptRuntime(IDebugHttpForwardService forwardService) {
        this.forwardService = forwardService;
    }

    /**
     * 执行脚本源码。
     *
     * @param language 持久化 language：{@code javascript} 或 {@code python}
     * @param source   脚本正文
     * @param timeoutMs 本步超时（毫秒）
     * @param ctx      当前 Run 上下文（flow 写入会直接反映到该对象）
     */
    public ScriptExecutionResult execute(String language, String source, long timeoutMs, FlowRunContext ctx) {
        if (!ScriptConstants.isSupportedLanguage(language)) {
            return ScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    "不支持的脚本语言: " + language
            );
        }
        if (source == null || source.isBlank()) {
            return ScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    "脚本源码为空"
            );
        }
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ScriptConstants.MAX_SOURCE_BYTES) {
            return ScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    "脚本源码超过 " + ScriptConstants.MAX_SOURCE_BYTES + " 字节"
            );
        }

        boolean acquired = false;
        try {
            acquired = CONCURRENCY.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                return ScriptExecutionResult.fail(
                        FlowErrorCode.TF_SCRIPT_ERROR,
                        "脚本并发执行数已达上限"
                );
            }
            return runWithTimeout(language, source, timeoutMs, ctx);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    "等待脚本执行槽位被中断"
            );
        } finally {
            if (acquired) {
                CONCURRENCY.release();
            }
        }
    }

    /**
     * 执行 API 前置/后置脚本（JavaScript only，注入 {@code api} 宿主）。
     */
    public ApiScriptExecutionResult executeApiScript(
            ApiScriptPhase phase,
            String source,
            ApiScriptContext scriptCtx,
            long timeoutMs,
            IDebugHttpForwardService forwardService
    ) {
        if (source == null || source.isBlank()) {
            return ApiScriptExecutionResult.skip(scriptCtx);
        }
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ScriptConstants.MAX_SOURCE_BYTES) {
            return ApiScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    "脚本源码超过 " + ScriptConstants.MAX_SOURCE_BYTES + " 字节",
                    scriptCtx
            );
        }

        boolean acquired = false;
        try {
            acquired = CONCURRENCY.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                return ApiScriptExecutionResult.fail(
                        FlowErrorCode.TF_SCRIPT_ERROR,
                        "脚本并发执行数已达上限",
                        scriptCtx
                );
            }
            return runApiScriptWithTimeout(phase, source, scriptCtx, timeoutMs, forwardService);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    "等待脚本执行槽位被中断",
                    scriptCtx
            );
        } finally {
            if (acquired) {
                CONCURRENCY.release();
            }
        }
    }

    private ApiScriptExecutionResult runApiScriptWithTimeout(
            ApiScriptPhase phase,
            String source,
            ApiScriptContext scriptCtx,
            long timeoutMs,
            IDebugHttpForwardService forwardService
    ) {
        Future<ApiScriptExecutionResult> future = executor.submit(
                () -> runApiScriptInContext(phase, source, scriptCtx, forwardService)
        );
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ApiScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_TIMEOUT,
                    "脚本执行超时（" + timeoutMs + "ms）",
                    scriptCtx
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return ApiScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    cause != null ? cause.getMessage() : e.getMessage(),
                    scriptCtx
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return ApiScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    "脚本执行被中断",
                    scriptCtx
            );
        }
    }

    private ApiScriptExecutionResult runApiScriptInContext(
            ApiScriptPhase phase,
            String source,
            ApiScriptContext scriptCtx,
            IDebugHttpForwardService forwardService
    ) {
        scriptCtx.setPhase(phase);
        List<String> logs = scriptCtx.getLogs();
        ApiScriptHost host = new ApiScriptHost(scriptCtx, forwardService);
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        String graalLang = ScriptConstants.toGraalLanguageId(ScriptConstants.LANGUAGE_JAVASCRIPT);

        try (Context context = buildContext(graalLang, outBuffer)) {
            context.getBindings(graalLang).putMember("api", host);
            context.eval(graalLang, source);
            captureStdout(outBuffer, logs);
            return ApiScriptExecutionResult.ok(scriptCtx);
        } catch (PolyglotException e) {
            captureStdout(outBuffer, logs);
            String message = e.getMessage() != null ? e.getMessage() : "脚本执行失败";
            return ApiScriptExecutionResult.fail(FlowErrorCode.TF_SCRIPT_ERROR, message, scriptCtx);
        } catch (Exception e) {
            captureStdout(outBuffer, logs);
            return ApiScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    e.getMessage() != null ? e.getMessage() : "脚本执行异常",
                    scriptCtx
            );
        }
    }

    private ScriptExecutionResult runWithTimeout(
            String language,
            String source,
            long timeoutMs,
            FlowRunContext ctx
    ) {
        Future<ScriptExecutionResult> future = executor.submit(
                () -> runInContext(language, source, ctx)
        );
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_TIMEOUT,
                    "脚本执行超时（" + timeoutMs + "ms）"
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return ScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    cause != null ? cause.getMessage() : e.getMessage()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return ScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    "脚本执行被中断"
            );
        }
    }

    private ScriptExecutionResult runInContext(String language, String source, FlowRunContext ctx) {
        List<String> logs = new ArrayList<>();
        ScriptHostContext hostCtx = new ScriptHostContext(ctx, logs, forwardService);
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        String graalLang = ScriptConstants.toGraalLanguageId(language);

        try (Context context = buildContext(graalLang, outBuffer)) {
            context.getBindings(graalLang).putMember("ctx", hostCtx);
            context.eval(graalLang, source);
            captureStdout(outBuffer, logs);
            return ScriptExecutionResult.ok(hostCtx.getWrites(), logs);
        } catch (PolyglotException e) {
            captureStdout(outBuffer, logs);
            String message = e.getMessage() != null ? e.getMessage() : "脚本执行失败";
            return ScriptExecutionResult.fail(FlowErrorCode.TF_SCRIPT_ERROR, message);
        } catch (Exception e) {
            captureStdout(outBuffer, logs);
            return ScriptExecutionResult.fail(
                    FlowErrorCode.TF_SCRIPT_ERROR,
                    e.getMessage() != null ? e.getMessage() : "脚本执行异常"
            );
        }
    }

    private static Context buildContext(String graalLang, ByteArrayOutputStream outBuffer) {
        Context.Builder builder = Context.newBuilder(graalLang)
                .engine(SHARED_ENGINE)
                .allowExperimentalOptions(true)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowHostAccess(org.graalvm.polyglot.HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .out(new PrintStream(outBuffer, true, StandardCharsets.UTF_8))
                .err(new PrintStream(outBuffer, true, StandardCharsets.UTF_8));
        if ("js".equals(graalLang)) {
            builder.option("js.ecmascript-version", "2022");
        }
        if ("python".equals(graalLang)) {
            builder.option("python.ForceImportSite", "false");
        }
        return builder.build();
    }

    private static void captureStdout(ByteArrayOutputStream outBuffer, List<String> logs) {
        String text = outBuffer.toString(StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return;
        }
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                logs.add(line);
            }
        }
    }
}
