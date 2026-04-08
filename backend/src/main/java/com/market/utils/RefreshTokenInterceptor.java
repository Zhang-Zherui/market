package com.market.utils;

import com.market.dto.UserDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Token 刷新拦截器
 * 从 JWT access token 中解析用户信息，存入 ThreadLocal
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private JwtUtils jwtUtils;

    public RefreshTokenInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");

        // 2. token 为空，放行交给 LoginInterceptor 处理
        if (token == null) {
            return true;
        }

        try {
            // 3. 解析 JWT token
            Claims claims = jwtUtils.parseToken(token);

            // 4. 检查 token 类型（只接受 access token）
            String type = claims.get("type", String.class);
            if (!"access".equals(type)) {
                return true;
            }

            // 5. 获取用户ID，构建 UserDTO 存入 ThreadLocal
            Long userId = claims.get("userId", Long.class);
            UserDTO userDTO = new UserDTO();
            userDTO.setId(userId);
            UserHolder.saveUser(userDTO);
        } catch (ExpiredJwtException e) {
            // access token 过期，放行交给 LoginInterceptor 返回明确错误
            return true;
        } catch (Exception e) {
            // token 无效，放行交给 LoginInterceptor 处理
            return true;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
