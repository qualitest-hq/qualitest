package com.qualitest.flow.context;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 流程 Run 占位符解析器 Bean 注册。
 */
@Configuration
public class FlowPlaceholderResolverConfig {

    /** 正式 Run 使用严格模式：未定义占位符抛错 */
    @Bean
    PlaceholderResolver flowPlaceholderResolver() {
        return PlaceholderResolver.strict();
    }
}