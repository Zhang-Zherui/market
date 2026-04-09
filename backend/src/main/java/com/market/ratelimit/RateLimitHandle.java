package com.market.ratelimit;

public interface RateLimitHandle {

    boolean isAcquired();

    void exit();
}
