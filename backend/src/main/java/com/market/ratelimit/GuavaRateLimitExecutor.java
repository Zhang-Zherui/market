package com.market.ratelimit;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
public class GuavaRateLimitExecutor implements RateLimitExecutor {

    private final ConcurrentMap<String, RateLimiter> rateLimiterMap = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "guava";
    }

    @Override
    public RateLimitHandle acquire(String resource, double permitsPerSecond, long timeoutMs) {
        String key = resource + ":" + permitsPerSecond;
        RateLimiter rateLimiter = rateLimiterMap.computeIfAbsent(key, k -> RateLimiter.create(permitsPerSecond));
        boolean acquired = rateLimiter.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        return new RateLimitHandle() {
            @Override
            public boolean isAcquired() {
                return acquired;
            }

            @Override
            public void exit() {
                // guava 无需释放
            }
        };
    }
}
