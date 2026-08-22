package com.qualitest.common.core.controller;

import com.qualitest.common.constant.HttpStatus;
import com.qualitest.common.core.domain.AjaxResult;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.domain.model.LoginUser;
import com.qualitest.common.core.page.PageDomain;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.page.TableSupport;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.common.utils.PageUtils;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.common.utils.StringUtils;
import com.qualitest.common.utils.sql.SqlUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;
import java.util.Date;
import java.util.List;

/**
 * web层通用数据处理
 *
 * @author qualitest
 */
public class BaseController {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 将前台传递过来的日期格式的字符串，自动转化为Date类型
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // Date 类型转换
        binder.registerCustomEditor(Date.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(DateUtils.parseDate(text));
            }
        });
    }

    /**
     * 设置请求分页数据
     */
    protected void startPage() {
        PageUtils.startPage();
    }

    /**
     * 设置请求排序数据
     */
    protected void startOrderBy() {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        if (StringUtils.isNotEmpty(pageDomain.getOrderBy())) {
            String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
            PageHelper.orderBy(orderBy);
        }
    }

    /**
     * 清理分页的线程变量
     */
    protected void clearPage() {
        PageUtils.clearPage();
    }

    /**
     * 响应请求分页数据
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected TableDataInfo getDataTable(List<?> list) {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        return rspData;
    }

    /**
     * 返回成功
     */
    public AjaxResult success() {
        return AjaxResult.success();
    }

    /**
     * 返回失败消息
     */
    public AjaxResult error() {
        return AjaxResult.error();
    }

    /**
     * 返回成功消息
     */
    public AjaxResult success(String message) {
        return AjaxResult.success(message);
    }

    /**
     * 返回成功消息
     */
    public AjaxResult success(Object data) {
        return AjaxResult.success(data);
    }

    /**
     * 返回失败消息
     */
    public AjaxResult error(String message) {
        return AjaxResult.error(message);
    }

    /**
     * 返回警告消息
     */
    public AjaxResult warn(String message) {
        return AjaxResult.warn(message);
    }

    /**
     * 响应返回结果
     *
     * @param rows 影响行数
     * @return 操作结果
     */
    protected AjaxResult toAjax(int rows) {
        return rows > 0 ? AjaxResult.success() : AjaxResult.error();
    }

    /**
     * 响应返回结果
     *
     * @param result 结果
     * @return 操作结果
     */
    protected AjaxResult toAjax(boolean result) {
        return result ? success() : error();
    }

    // ==================== R 类支持方法 ====================

    /**
     * 返回成功消息（R类型）
     *
     * @param <T> 数据类型
     * @return 成功消息
     */
    public <T> R<T> ok() {
        return R.ok();
    }

    /**
     * 返回成功数据（R类型）
     *
     * @param <T> 数据类型
     * @param data 数据对象
     * @return 成功消息
     */
    public <T> R<T> ok(T data) {
        return R.ok(data);
    }

    /**
     * 返回成功消息（R类型）
     *
     * @param <T> 数据类型
     * @param data 数据对象
     * @param msg 返回内容
     * @return 成功消息
     */
    public <T> R<T> ok(T data, String msg) {
        return R.ok(data, msg);
    }

    /**
     * 返回失败消息（R类型）
     *
     * @param <T> 数据类型
     * @return 失败消息
     */
    public <T> R<T> fail() {
        return R.fail();
    }

    /**
     * 返回失败消息（R类型）
     *
     * @param <T> 数据类型
     * @param msg 返回内容
     * @return 失败消息
     */
    public <T> R<T> fail(String msg) {
        return R.fail(msg);
    }

    /**
     * 返回失败消息（R类型）
     *
     * @param <T> 数据类型
     * @param data 数据对象
     * @return 失败消息
     */
    public <T> R<T> fail(T data) {
        return R.fail(data);
    }

    /**
     * 返回失败消息（R类型）
     *
     * @param <T> 数据类型
     * @param data 数据对象
     * @param msg 返回内容
     * @return 失败消息
     */
    public <T> R<T> fail(T data, String msg) {
        return R.fail(data, msg);
    }

    /**
     * 返回失败消息（R类型）
     *
     * @param <T> 数据类型
     * @param code 状态码
     * @param msg 返回内容
     * @return 失败消息
     */
    public <T> R<T> fail(int code, String msg) {
        return R.fail(code, msg);
    }

    /**
     * 响应返回结果（R类型）
     *
     * @param <T> 数据类型
     * @param rows 影响行数
     * @return 操作结果
     */
    protected <T> R<T> toR(int rows) {
        return rows > 0 ? R.ok() : R.fail();
    }

    /**
     * 响应返回结果（R类型）
     *
     * @param <T> 数据类型
     * @param result 结果
     * @return 操作结果
     */
    protected <T> R<T> toR(boolean result) {
        return result ? ok() : fail();
    }

    /**
     * 响应返回结果（R类型，带消息）
     *
     * @param <T> 数据类型
     * @param rows 影响行数
     * @param successMsg 成功消息
     * @param failMsg 失败消息
     * @return 操作结果
     */
    protected <T> R<T> toR(int rows, String successMsg, String failMsg) {
        return rows > 0 ? R.ok(null, successMsg) : R.fail(failMsg);
    }

    /**
     * 响应返回结果（R类型，带消息）
     *
     * @param <T> 数据类型
     * @param result 结果
     * @param successMsg 成功消息
     * @param failMsg 失败消息
     * @return 操作结果
     */
    protected <T> R<T> toR(boolean result, String successMsg, String failMsg) {
        return result ? R.ok(null, successMsg) : R.fail(failMsg);
    }

    /**
     * 页面跳转
     */
    public String redirect(String url) {
        return StringUtils.format("redirect:{}", url);
    }

    /**
     * 获取用户缓存信息
     */
    public LoginUser getLoginUser() {
        return SecurityUtils.getLoginUser();
    }

    /**
     * 获取登录用户id
     */
    public Long getUserId() {
        return getLoginUser().getUserId();
    }

    /**
     * 获取登录部门id
     */
    public Long getDeptId() {
        return getLoginUser().getDeptId();
    }

    /**
     * 获取登录用户名
     */
    public String getUsername() {
        return getLoginUser().getUsername();
    }

    /**
     * 是否为管理员
     *
     * @return 结果
     */
    public boolean isAdmin() {
        return SecurityUtils.isAdmin();
    }

}
