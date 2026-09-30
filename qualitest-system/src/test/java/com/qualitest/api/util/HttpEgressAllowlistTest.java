package com.qualitest.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 HTTP 出站白名单：关闭时放行任意合法目标；开启时按 host:port 拦截。
 * 边界：协议校验、缺省端口 80/443、IPv6、域名条目、错误端口。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpEgressAllowlistTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpEgressAllowlistTest {

    /** 测试用允许目标：本机别名与回环 + 端口 8801 */
    private static final String DEMO_LIST =
            "host.docker.internal:8801,localhost:8801,127.0.0.1:8801,[::1]:8801";

    /**
     * 前提：白名单关闭。
     * 期望：任意合法 http(s) URL 均通过，含公网与非名单端口。
     */
    @Test
    @Order(1)
    @DisplayName("关闭时放行任意 http(s) 目标")
    void check_disabled_allowsAnyHost() {
        HttpEgressAllowlist policy = new HttpEgressAllowlist(false, DEMO_LIST);
        assertNull(policy.check("https://evil.com/token"));
        assertNull(policy.check("http://host.docker.internal:8801/api"));
        assertNull(policy.check("http://127.0.0.1:3306/"));
    }

    /**
     * 前提：白名单开启，名单含 host.docker.internal:8801 等。
     * 期望：名单内本机靶场 URL 通过。
     */
    @Test
    @Order(2)
    @DisplayName("开启时放行名单内 host:port")
    void check_enabled_allowsListedTarget() {
        HttpEgressAllowlist policy = new HttpEgressAllowlist(true, DEMO_LIST);
        assertNull(policy.check("http://host.docker.internal:8801"));
        assertNull(policy.check("http://host.docker.internal:8801/login"));
        assertNull(policy.check("http://localhost:8801/test-support"));
        assertNull(policy.check("http://127.0.0.1:8801/"));
    }

    /**
     * 前提：白名单开启。
     * 期望：名单外主机即使端口同为 8801 也拒绝。
     */
    @Test
    @Order(3)
    @DisplayName("开启时拒绝名单外主机")
    void check_enabled_rejectsForeignHost() {
        HttpEgressAllowlist policy = new HttpEgressAllowlist(true, DEMO_LIST);
        String err = policy.check("https://evil.com:8801/x");
        assertTrue(err != null && err.contains("白名单"));
    }

    /**
     * 前提：白名单开启；主机在名单内但端口为 3306。
     * 期望：因 host:port 不成对匹配而拒绝。
     */
    @Test
    @Order(4)
    @DisplayName("开启时拒绝同 host 错误端口")
    void check_enabled_rejectsWrongPort() {
        HttpEgressAllowlist policy = new HttpEgressAllowlist(true, DEMO_LIST);
        String err = policy.check("http://host.docker.internal:3306/");
        assertTrue(err != null && err.contains("白名单"));
        assertTrue(err.contains("3306"));
    }

    /**
     * 前提：白名单开启；URL 未写端口。
     * 期望：按 http→80、https→443 参与比对，未在名单则拒绝。
     */
    @Test
    @Order(5)
    @DisplayName("开启时缺省端口按 80/443 校验")
    void check_enabled_defaultPortsNotInList() {
        HttpEgressAllowlist policy = new HttpEgressAllowlist(true, DEMO_LIST);
        assertTrue(policy.check("http://host.docker.internal/") != null);
        assertTrue(policy.check("https://host.docker.internal/") != null);
    }

    /**
     * 前提：空 URL、ftp 协议、或缺主机。
     * 期望：基础校验失败；与白名单开关无关。
     */
    @Test
    @Order(6)
    @DisplayName("拒绝非 http(s) 与空 URL")
    void check_rejectsBadSchemeAndBlank() {
        HttpEgressAllowlist policy = new HttpEgressAllowlist(false, DEMO_LIST);
        assertEquals("目标 URL 不能为空", policy.check("  "));
        assertTrue(policy.check("ftp://example.com/file") != null);
        assertTrue(policy.check("https:///path") != null);
    }

    /**
     * 前提：白名单含 [::1]:8801。
     * 期望：http://[::1]:8801 通过；规范化键为 [::1]:8801。
     */
    @Test
    @Order(7)
    @DisplayName("开启时放行名单内 IPv6")
    void check_enabled_allowsIpv6Loopback() {
        HttpEgressAllowlist policy = new HttpEgressAllowlist(true, DEMO_LIST);
        assertNull(policy.check("http://[::1]:8801/"));
        assertEquals("[::1]:8801", HttpEgressAllowlist.toHostPortKey(URI.create("http://[::1]:8801/x")));
    }

    /**
     * 前提：白名单仅含域名 demo.example.com:443。
     * 期望：该域名 https 默认 443 通过；其它域名拒绝。
     */
    @Test
    @Order(8)
    @DisplayName("开启时支持域名条目")
    void check_enabled_allowsDomainEntry() {
        HttpEgressAllowlist policy = new HttpEgressAllowlist(true, "demo.example.com:443");
        assertNull(policy.check("https://demo.example.com/v1"));
        assertTrue(policy.check("https://other.example.com/") != null);
    }
}
