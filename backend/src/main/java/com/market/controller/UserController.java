package com.market.controller;


import com.market.dto.LoginFormDTO;
import com.market.dto.RegisterFormDTO;
import com.market.dto.Result;
import com.market.dto.UserDTO;
import com.market.entity.User;
import com.market.service.IUserService;
import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    /**
     * 发送邮箱验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("email") String email) throws MessagingException {
        return userService.sendCode(email);
    }

    /**
     * 注册（邮箱+验证码+密码）
     */
    @PostMapping("/register")
    public Result register(@RequestBody RegisterFormDTO registerForm) {
        return userService.register(registerForm);
    }

    /**
     * 修改密码（邮箱+验证码+新密码）
     */
    @PostMapping("/password")
    public Result changePassword(@RequestBody RegisterFormDTO form) {
        return userService.changePassword(form);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含邮箱、验证码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /**
     * 刷新 access token
     * @param loginForm 包含 refresh_token
     */
    @PostMapping("/refresh")
    public Result refreshToken(@RequestBody LoginFormDTO loginForm) {
        return userService.refreshToken(loginForm.getRefreshToken());
    }

    /**
     * 登出功能
     */
    @PostMapping("/logout")
    public Result logout() {
        return userService.logout();
    }

    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 查询当前登录用户信息
     */
    @GetMapping("/me")
    public Result queryMe() {
        Long userId = com.market.utils.UserHolder.getUser().getId();
        return queryById(userId);
    }

    /**
     * 修改用户昵称
     * @param params 包含 nickName
     * @return 操作结果
     */
    @PutMapping
    public Result updateNickName(@RequestBody Map<String, String> params) {
        return userService.updateNickName(params.get("nickName"));
    }

}
