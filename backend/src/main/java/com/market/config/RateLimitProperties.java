package com.market.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /**
     * 是否开启限流
     */
    private boolean enabled = true;

    /**
     * 当前限流实现类型，可选 guava / sentinel
     */
    private String type = "guava";
}
