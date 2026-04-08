package com.market.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.market.dto.LoginFormDTO;
import com.market.dto.RegisterFormDTO;
import com.market.dto.Result;
import com.market.entity.User;

import javax.mail.MessagingException;
import java.util.Map;

public interface IUserService extends IService<User> {

    Result sendCode(String email) throws MessagingException;

    Result register(RegisterFormDTO registerForm);

    Result login(LoginFormDTO loginForm);

    Result refreshToken(String refreshToken);

    Result logout();

    Result changePassword(RegisterFormDTO form);

    /**
     * 修改用户昵称
     * @param nickName 新昵称
     * @return 操作结果
     */
    Result updateNickName(String nickName);
}
