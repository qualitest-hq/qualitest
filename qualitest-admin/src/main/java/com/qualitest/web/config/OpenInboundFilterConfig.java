package com.qualitest.web.config;

import com.qualitest.flow.inbound.OpenInboundExecutionService;
import com.qualitest.flow.inbound.OpenInboundPathIndex;
import com.qualitest.web.filter.OpenInboundFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将开放入站 Filter 挂到 Servlet 容器链，顺序早于 Spring Security。
 */
@Configuration
public class OpenInboundFilterConfig {

    @Bean
    public FilterRegistrationBean<OpenInboundFilter> openInboundFilterRegistration(
            OpenInboundPathIndex pathIndex,
            OpenInboundExecutionService openInboundExecutionService) {
        OpenInboundFilter filter = new OpenInboundFilter(pathIndex, openInboundExecutionService);
        FilterRegistrationBean<OpenInboundFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/*");
        reg.setOrder(OpenInboundFilter.FILTER_ORDER);
        reg.setName("openInboundFilter");
        return reg;
    }
}
