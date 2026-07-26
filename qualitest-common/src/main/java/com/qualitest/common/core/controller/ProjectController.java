package com.qualitest.common.core.controller;

import com.qualitest.common.constant.ProjectConstants;
import com.qualitest.common.core.domain.R;
import com.qualitest.project.domain.TestProjectUserSetting;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 测试项目控制器基类
 *
 * @author qualitest
 */
@Slf4j
public class ProjectController {

    /**
     * 获取当前请求的 HttpServletRequest
     */
    protected HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("无法获取HttpServletRequest");
    }

    /**
     * 获取测试项目用户设置
     *
     * @return 项目用户设置对象
     */
    protected TestProjectUserSetting getTestProjectUserSetting() {
        HttpServletRequest request = getRequest();
        return (TestProjectUserSetting) request.getAttribute(ProjectConstants.PROJECT_SETTING_ATTR);
    }

    /**
     * 获取登录用户ID
     */
    protected Long getUserId() {
        return getTestProjectUserSetting().getUserId();
    }

    /**
     * 获取测试项目ID
     */
    protected Long getTestProjectId() {
        return getTestProjectUserSetting().getTestProjectId();
    }

    // ==================== R 类支持方法 ====================

    /**
     * 返回成功消息
     *
     * @param <T> 数据类型
     * @return 成功消息
     */
    public <T> R<T> ok() {
        return R.ok();
    }

    /**
     * 返回成功数据
     *
     * @param <T>  数据类型
     * @param data 数据对象
     * @return 成功消息
     */
    public <T> R<T> ok(T data) {
        return R.ok(data);
    }

    /**
     * 返回成功消息
     *
     * @param <T>  数据类型
     * @param data 数据对象
     * @param msg  返回内容
     * @return 成功消息
     */
    public <T> R<T> ok(T data, String msg) {
        return R.ok(data, msg);
    }

    /**
     * 返回失败消息
     *
     * @param <T> 数据类型
     * @return 失败消息
     */
    public <T> R<T> fail() {
        return R.fail();
    }

    /**
     * 返回失败消息
     *
     * @param <T> 数据类型
     * @param msg 返回内容
     * @return 失败消息
     */
    public <T> R<T> fail(String msg) {
        return R.fail(msg);
    }

    /**
     * 返回失败消息
     *
     * @param <T>  数据类型
     * @param data 数据对象
     * @return 失败消息
     */
    public <T> R<T> fail(T data) {
        return R.fail(data);
    }

    /**
     * 返回失败消息
     *
     * @param <T>  数据类型
     * @param data 数据对象
     * @param msg  返回内容
     * @return 失败消息
     */
    public <T> R<T> fail(T data, String msg) {
        return R.fail(data, msg);
    }

    /**
     * 返回失败消息
     *
     * @param <T>  数据类型
     * @param code 状态码
     * @param msg  返回内容
     * @return 失败消息
     */
    public <T> R<T> fail(int code, String msg) {
        return R.fail(code, msg);
    }

    /**
     * 响应返回结果
     *
     * @param <T>  数据类型
     * @param rows 影响行数
     * @return 操作结果
     */
    protected <T> R<T> toR(int rows) {
        return rows > 0 ? R.ok() : R.fail();
    }

    /**
     * 响应返回结果
     *
     * @param <T>    数据类型
     * @param result 结果
     * @return 操作结果
     */
    protected <T> R<T> toR(boolean result) {
        return result ? ok() : fail();
    }

    /**
     * 响应返回结果（R类型，带消息）
     *
     * @param <T>        数据类型
     * @param rows       影响行数
     * @param successMsg 成功消息
     * @param failMsg    失败消息
     * @return 操作结果
     */
    protected <T> R<T> toR(int rows, String successMsg, String failMsg) {
        return rows > 0 ? R.ok(null, successMsg) : R.fail(failMsg);
    }

    /**
     * 响应返回结果（R类型，带消息）
     *
     * @param <T>        数据类型
     * @param result     结果
     * @param successMsg 成功消息
     * @param failMsg    失败消息
     * @return 操作结果
     */
    protected <T> R<T> toR(boolean result, String successMsg, String failMsg) {
        return result ? R.ok(null, successMsg) : R.fail(failMsg);
    }
}