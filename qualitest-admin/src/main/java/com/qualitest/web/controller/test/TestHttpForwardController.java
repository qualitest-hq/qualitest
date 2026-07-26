package com.qualitest.web.controller.test;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.service.IDebugHttpForwardService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 调试 HTTP 转发（需登录 JWT；路径与 /api/project 的 Project Token 分离）
 */
@Slf4j
@RestController
@RequestMapping("/test")
@AllArgsConstructor
public class TestHttpForwardController {

    private final IDebugHttpForwardService debugHttpForwardService;

    @PostMapping("/http-forward")
    public DebugHttpForwardResult httpForward(@RequestBody DebugHttpForwardParams params) {
        log.debug("test http-forward correlationId={} url={}",
                params != null ? params.getCorrelationId() : null,
                params != null ? params.getUrl() : null);
        return debugHttpForwardService.forward(params);
    }
}
