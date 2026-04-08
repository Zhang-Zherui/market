package com.market.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.market.dto.LoginFormDTO;
import com.market.dto.RegisterFormDTO;
import com.market.dto.Result;
import com.market.entity.User;
import com.market.mapper.UserMapper;
import com.market.service.IUserService;
import com.market.utils.JwtUtils;
import com.market.utils.MailUtils;
import com.market.utils.RegexUtils;
import com.market.utils.SystemConstants;
import com.market.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.market.utils.RedisConstants.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private MailUtils mailUtils;

    @Value("${jwt.access-token-ttl}")
    private Long accessTokenTtl;

    @Value("${jwt.refresh-token-ttl}")
    private Long refreshTokenTtl;

    @Override
    public Result sendCode(String email) throws MessagingException {
        // 1. 校验邮箱格式
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式不正确");
        }

        // 2. 判断是否在一级限制条件内
        Boolean oneLevelLimit = stringRedisTemplate.opsForSet().isMember(ONE_LEVERLIMIT_KEY + email, "1");
        if (Boolean.TRUE.equals(oneLevelLimit)) {
            return Result.fail("您需要等5分钟后再请求");
        }

        // 3. 判断是否在二级限制条件内
        Boolean twoLevelLimit = stringRedisTemplate.opsForSet().isMember(TWO_LEVERLIMIT_KEY + email, "1");
        if (Boolean.TRUE.equals(twoLevelLimit)) {
            return Result.fail("您需要等20分钟后再请求");
        }

        // 4. 检查过去1分钟内发送验证码的次数
        long oneMinuteAgo = System.currentTimeMillis() - 60 * 1000;
        long countOneMinute = stringRedisTemplate.opsForZSet()
                .count(SENDCODE_SENDTIME_KEY + email, oneMinuteAgo, System.currentTimeMillis());
        if (countOneMinute >= 1) {
            return Result.fail("距离上次发送时间不足1分钟，请1分钟后重试");
        }

        // 5. 检查过去5分钟内发送验证码的次数
        long fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000;
        long countFiveMinute = stringRedisTemplate.opsForZSet()
                .count(SENDCODE_SENDTIME_KEY + email, fiveMinutesAgo, System.currentTimeMillis());
        if (countFiveMinute % 3 == 2 && countFiveMinute > 5) {
            // 发送了8, 11, 14, ...次，进入二级限制
            stringRedisTemplate.opsForSet().add(TWO_LEVERLIMIT_KEY + email, "1");
            stringRedisTemplate.expire(TWO_LEVERLIMIT_KEY + email, 20, TimeUnit.MINUTES);
            return Result.fail("接下来如需再发送，请等20分钟后再请求");
        } else if (countFiveMinute == 5) {
            // 过去5分钟内已经发送了5次，进入一级限制
            stringRedisTemplate.opsForSet().add(ONE_LEVERLIMIT_KEY + email, "1");
            stringRedisTemplate.expire(ONE_LEVERLIMIT_KEY + email, 5, TimeUnit.MINUTES);
            return Result.fail("5分钟内已经发送了5次，接下来如需再发送请等待5分钟后重试");
        }

        // 6. 生成验证码并发送
        String code = mailUtils.achieveCode();
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + email, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送邮箱验证码：email={}, code={}", email, code);
        mailUtils.sendtoMail(email, code);

        // 7. 记录发送时间到 ZSet
        stringRedisTemplate.opsForZSet()
                .add(SENDCODE_SENDTIME_KEY + email, System.currentTimeMillis() + "", System.currentTimeMillis());

        return Result.ok();
    }

    @Override
    public Result register(RegisterFormDTO registerForm) {
        String email = registerForm.getEmail();
        String code = registerForm.getCode();
        String password = registerForm.getPassword();

        // 1. 校验邮箱格式
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式不正确");
        }

        // 2. 校验密码
        if (password == null || password.length() < 6) {
            return Result.fail("密码长度不能少于6位");
        }

        // 3. 从 Redis 中读取验证码并校验
        String cachedCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        if (cachedCode == null || !code.equals(cachedCode)) {
            return Result.fail("无效的验证码");
        }

        // 4. 检查邮箱是否已注册
        User existUser = query().eq("email", email).one();
        if (existUser != null) {
            return Result.fail("该邮箱已注册");
        }

        // 5. 创建用户
        User user = new User();
        user.setEmail(email);
        user.setPassword(md5(password));
        String nickName = registerForm.getNickName();
        user.setNickName((nickName != null && !nickName.isEmpty()) ? nickName : SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);

        // 6. 删除验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + email);

        return Result.ok();
    }

    @Override
    public Result changePassword(RegisterFormDTO form) {
        String email = form.getEmail();
        String code = form.getCode();
        String password = form.getPassword();

        // 1. 校验邮箱格式
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式不正确");
        }

        // 2. 校验密码
        if (password == null || password.length() < 6) {
            return Result.fail("密码长度不能少于6位");
        }

        // 3. 从 Redis 中读取验证码并校验
        String cachedCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        if (cachedCode == null || !code.equals(cachedCode)) {
            return Result.fail("无效的验证码");
        }

        // 4. 查找用户
        User user = query().eq("email", email).one();
        if (user == null) {
            return Result.fail("该邮箱未注册");
        }

        // 5. 更新密码
        user.setPassword(md5(password));
        updateById(user);

        // 6. 删除验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + email);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        String email = loginForm.getEmail();
        String password = loginForm.getPassword();

        // 1. 校验邮箱格式
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式不正确");
        }

        // 2. 校验密码
        if (password == null || password.isEmpty()) {
            return Result.fail("密码不能为空");
        }

        // 3. 查询用户
        User user = query().eq("email", email).one();
        if (user == null || !md5(password).equals(user.getPassword())) {
            return Result.fail("邮箱或密码错误");
        }

        // 4. 生成双 Token
        String accessToken = jwtUtils.createAccessToken(user.getId());
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // 5. 将 refresh token 存入 Redis（关联用户ID）
        String refreshTokenKey = REFRESH_TOKEN_KEY + refreshToken;
        stringRedisTemplate.opsForValue().set(refreshTokenKey, user.getId().toString(), REFRESH_TOKEN_TTL, TimeUnit.MINUTES);

        // 6. 存储 userId -> refreshToken 映射（用于登出时查找）
        stringRedisTemplate.opsForValue().set(LOGIN_USER_TOKEN_KEY + user.getId(), refreshToken, REFRESH_TOKEN_TTL, TimeUnit.MINUTES);

        // 7. 返回双 Token
        Map<String, String> tokens = new HashMap<>();
        tokens.put("access_token", accessToken);
        tokens.put("refresh_token", refreshToken);
        return Result.ok(tokens);
    }

    @Override
    public Result refreshToken(String refreshToken) {
        if (refreshToken == null) {
            return Result.fail("refresh_token 不能为空");
        }

        // 1. 从 Redis 中查找 refresh token
        String refreshTokenKey = REFRESH_TOKEN_KEY + refreshToken;
        String userIdStr = stringRedisTemplate.opsForValue().get(refreshTokenKey);

        if (userIdStr == null) {
            return Result.fail("refresh_token 已过期或无效");
        }

        Long userId = Long.parseLong(userIdStr);

        // 2. 生成新的 access token
        String newAccessToken = jwtUtils.createAccessToken(userId);

        // 3. 生成新的 refresh token（刷新时也更新 refresh token）
        String newRefreshToken = UUID.randomUUID().toString().replace("-", "");
        String newRefreshTokenKey = REFRESH_TOKEN_KEY + newRefreshToken;
        stringRedisTemplate.opsForValue().set(newRefreshTokenKey, userIdStr, REFRESH_TOKEN_TTL, TimeUnit.MINUTES);

        // 4. 删除旧的 refresh token（实现 refresh token 轮换）
        stringRedisTemplate.delete(refreshTokenKey);

        // 5. 更新 userId -> refreshToken 映射
        stringRedisTemplate.opsForValue().set(LOGIN_USER_TOKEN_KEY + userId, newRefreshToken, REFRESH_TOKEN_TTL, TimeUnit.MINUTES);

        // 6. 返回新的双 Token
        Map<String, String> tokens = new HashMap<>();
        tokens.put("access_token", newAccessToken);
        tokens.put("refresh_token", newRefreshToken);
        return Result.ok(tokens);
    }

    @Override
    public Result logout() {
        Long userId = UserHolder.getUser().getId();
        // 1. 通过 userId 查找对应的 refreshToken
        String userTokenKey = LOGIN_USER_TOKEN_KEY + userId;
        String refreshToken = stringRedisTemplate.opsForValue().get(userTokenKey);
        // 2. 删除 userId -> refreshToken 映射
        stringRedisTemplate.delete(userTokenKey);
        // 3. 如果 refreshToken 存在，也删除 token -> userId 映射
        if (refreshToken != null) {
            stringRedisTemplate.delete(REFRESH_TOKEN_KEY + refreshToken);
        }
        return Result.ok();
    }

    @Override
    public Result updateNickName(String nickName) {
        if (nickName == null || nickName.trim().isEmpty()) {
            return Result.fail("昵称不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        User user = new User();
        user.setId(userId);
        user.setNickName(nickName.trim());
        updateById(user);
        return Result.ok();
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(md5(""));
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5加密失败", e);
        }
    }
}
