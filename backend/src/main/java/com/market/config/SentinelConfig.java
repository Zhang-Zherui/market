package com.market.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Configuration
public class SentinelConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Resource
    private SentinelProperties sentinelProperties;

    @PostConstruct
    public void init() {
        if (!sentinelProperties.isEnabled()) {
            return;
        }
        System.setProperty("project.name", applicationName);
        System.setProperty("csp.sentinel.dashboard.server", sentinelProperties.getDashboardServer());
        System.setProperty("csp.sentinel.api.port", String.valueOf(sentinelProperties.getApiPort()));
        System.setProperty("csp.sentinel.heartbeat.client.ip", sentinelProperties.getClientIp());
    }

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }
}
