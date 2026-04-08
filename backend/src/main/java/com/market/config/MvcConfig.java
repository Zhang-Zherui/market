package com.market.config;

import com.market.utils.JwtUtils;
import com.market.utils.LoginInterceptor;
import com.market.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Token 解析拦截器（全局，order=0）
        registry.addInterceptor(new RefreshTokenInterceptor(jwtUtils)).order(0);

        // 登录校验拦截器（order=1，排除公开接口）
        registry.addInterceptor(new LoginInterceptor(jwtUtils))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/user/register",
                        "/user/password",
                        "/user/refresh",
                        "/voucher/**",
                        "/shop/**",
                        "/blog/hot"
                ).order(1);
    }
}
