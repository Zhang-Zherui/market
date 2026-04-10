package com.market.aspect;

import com.market.annotation.RateLimit;
import com.market.config.RateLimitProperties;
import com.market.dto.Result;
import com.market.ratelimit.RateLimitExecutorFactory;
import com.market.ratelimit.RateLimitHandle;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;

@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private RateLimitProperties rateLimitProperties;

    @Resource
    private RateLimitExecutorFactory rateLimitExecutorFactory;

    @Resource
    private MeterRegistry meterRegistry;

    @Around("@annotation(com.market.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!rateLimitProperties.isEnabled()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String resource = rateLimit.name();
        if (resource == null || resource.trim().isEmpty()) {
            resource = joinPoint.getTarget().getClass().getSimpleName() + "." + method.getName();
        }

        RateLimitHandle handle = rateLimitExecutorFactory.getCurrentExecutor()
                .acquire(resource, rateLimit.permitsPerSecond(), rateLimit.timeoutMs());

        if (!handle.isAcquired()) {
            meterRegistry.counter("market_rate_limit_rejected_total", "resource", resource).increment();
            if (Result.class.isAssignableFrom(signature.getReturnType())) {
                return Result.fail(rateLimit.message());
            }
            throw new IllegalStateException("限流注解当前仅支持返回 Result 的方法: " + resource);
        }

        try {
            return joinPoint.proceed();
        } finally {
            handle.exit();
        }
    }
}
