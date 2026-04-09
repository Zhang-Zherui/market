package com.market.ratelimit;

import com.market.config.RateLimitProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class RateLimitExecutorFactory {

    @Resource
    private List<RateLimitExecutor> executors;

    @Resource
    private RateLimitProperties rateLimitProperties;

    public RateLimitExecutor getCurrentExecutor() {
        String currentType = rateLimitProperties.getType();
        for (RateLimitExecutor executor : executors) {
            if (executor.type().equalsIgnoreCase(currentType)) {
                return executor;
            }
        }
        throw new IllegalStateException("未找到限流执行器，当前配置类型为: " + currentType);
    }
}
