package com.market.utils;

import cn.hutool.core.util.StrUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 * 检查 ThreadLocal 中是否有用户信息，没有则拦截
 */
public class LoginInterceptor implements HandlerInterceptor {

    private JwtUtils jwtUtils;

    public LoginInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null) {
            // 检查是否是因为 access token 过期
            String token = request.getHeader("authorization");
            if (StrUtil.isNotBlank(token)) {
                try {
                    jwtUtils.parseToken(token);
                } catch (ExpiredJwtException e) {
                    // token 已过期，返回特定状态码
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"errorMsg\":\"access_token 已过期，请刷新 token\"}");
                    return false;
                } catch (JwtException e) {
                    // token 无效（签名错误、格式错误等）
                    response.setStatus(401);
                    return false;
                }
            }
            // 无 token
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
