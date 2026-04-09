package com.market.ratelimit;

public interface RateLimitExecutor {

    /**
     * 当前执行器支持的类型
     */
    String type();

    RateLimitHandle acquire(String resource, double permitsPerSecond, long timeoutMs);
}
