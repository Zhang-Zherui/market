package com.market.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String REFRESH_TOKEN_KEY = "login:refresh:";
    public static final Long REFRESH_TOKEN_TTL = 1440L;
    // 用户ID -> refreshToken 的映射（用于登出时查找并删除）
    public static final String LOGIN_USER_TOKEN_KEY = "login:usertoken:";

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String CACHE_SHOP_TYPE_KEY = "cache:shoptype:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String SENDCODE_SENDTIME_KEY = "email:sendtime:";

    public static final String ONE_LEVERLIMIT_KEY = "limit:onelevel:";

    public static final String TWO_LEVERLIMIT_KEY = "limit:twolevel:";

    // 优惠券缓存key
    public static final String CACHE_VOUCHER_KEY = "cache:voucher:";

}
