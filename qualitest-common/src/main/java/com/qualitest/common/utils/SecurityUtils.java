package com.qualitest.common.utils;

import com.qualitest.common.constant.Constants;
import com.qualitest.common.constant.HttpStatus;
import com.qualitest.common.core.domain.entity.SysRole;
import com.qualitest.common.core.domain.model.LoginUser;
import com.qualitest.common.exception.ServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.PatternMatchUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 安全服务工具类。
 * 从当前请求安全上下文读取登录用户；无认证主体时抛未登录异常。
 *
 * @author qualitest
 */
public class SecurityUtils {

    /** 无登录主体时的提示文案 */
    private static final String UNAUTH_MSG = "未登录：缺少 Web 会话或操作者";

    /**
     * 当前登录用户 id；无登录态时抛未登录异常
     */
    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    /**
     * 当前登录用户部门 id
     */
    public static Long getDeptId() {
        return getLoginUser().getDeptId();
    }

    /**
     * 当前登录用户账号名
     */
    public static String getUsername() {
        return getLoginUser().getUsername();
    }

    /**
     * 取安全上下文中的登录用户。
     * 无认证、主体不是登录用户、或读取失败时抛未登录异常。
     */
    public static LoginUser getLoginUser() {
        try {
            Authentication authentication = getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
                throw unauthorized();
            }
            return loginUser;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw unauthorized();
        }
    }

    /** 构造未登录业务异常 */
    private static ServiceException unauthorized() {
        return new ServiceException(UNAUTH_MSG, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 当前请求的认证对象，可能为空
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 生成BCryptPasswordEncoder密码
     *
     * @param password 密码
     * @return 加密字符串
     */
    public static String encryptPassword(String password) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.encode(password);
    }

    /**
     * 判断密码是否相同
     *
     * @param rawPassword     真实密码
     * @param encodedPassword 加密后字符
     * @return 结果
     */
    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 是否为管理员
     *
     * @return 结果
     */
    public static boolean isAdmin() {
        return isAdmin(getUserId());
    }

    /**
     * 是否为管理员
     *
     * @param userId 用户ID
     * @return 结果
     */
    public static boolean isAdmin(Long userId) {
        return userId != null && 1L == userId;
    }

    /**
     * 验证用户是否具备某权限
     *
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    public static boolean hasPermi(String permission) {
        return hasPermi(getLoginUser().getPermissions(), permission);
    }

    /**
     * 判断是否包含权限
     *
     * @param authorities 权限列表
     * @param permission  权限字符串
     * @return 用户是否具备某权限
     */
    public static boolean hasPermi(Collection<String> authorities, String permission) {
        return authorities.stream().filter(StringUtils::hasText)
                .anyMatch(x -> Constants.ALL_PERMISSION.equals(x) || PatternMatchUtils.simpleMatch(x, permission));
    }

    /**
     * 验证用户是否拥有某个角色
     *
     * @param role 角色标识
     * @return 用户是否具备某角色
     */
    public static boolean hasRole(String role) {
        List<SysRole> roleList = getLoginUser().getUser().getRoles();
        Collection<String> roles = roleList.stream().map(SysRole::getRoleKey).collect(Collectors.toSet());
        return hasRole(roles, role);
    }

    /**
     * 判断是否包含角色
     *
     * @param roles 角色列表
     * @param role  角色
     * @return 用户是否具备某角色权限
     */
    public static boolean hasRole(Collection<String> roles, String role) {
        return roles.stream().filter(StringUtils::hasText)
                .anyMatch(x -> Constants.SUPER_ADMIN.equals(x) || PatternMatchUtils.simpleMatch(x, role));
    }

}
