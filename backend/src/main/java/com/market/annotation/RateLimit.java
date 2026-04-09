package com.market.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 资源名，不填时默认使用“类名.方法名”
     */
    String name() default "";

    /**
     * 每秒发放令牌数
     */
    double permitsPerSecond();

    /**
     * 获取令牌的最长等待时间，单位毫秒
     */
    long timeoutMs() default 1000L;

    /**
     * 限流失败时返回的提示文案
     */
    String message() default "当前请求过多，请稍后重试";
}
