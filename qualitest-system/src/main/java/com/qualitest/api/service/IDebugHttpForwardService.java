package com.qualitest.api.service;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.DebugHttpForwardResult;

/**
 * API 调试 HTTP 转发
 */
public interface IDebugHttpForwardService {

  DebugHttpForwardResult forward(DebugHttpForwardParams params);
}
