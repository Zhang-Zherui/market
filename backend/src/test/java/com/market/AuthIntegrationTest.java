package com.market;

import com.alibaba.fastjson.JSON;
import com.market.dto.Result;
import com.market.entity.User;
import com.market.service.IUserService;
import com.market.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.market.utils.RedisConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户认证集成测试
 * 覆盖: 注册、登录、刷新 Token、登出、修改密码、查询用户信息
 * 直接连接本地 Redis，MockMvc 模拟 HTTP 请求
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private IUserService userService;

    private static final String TEST_EMAIL = "test_auth@example.com";
    private static final String TEST_PASSWORD = "test123456";
    private static final String TEST_CODE = "123456";
    private static final String TEST_NICKNAME = "测试用户";

    private static String accessToken;
    private static String refreshToken;
    private static Long testUserId;

    @BeforeAll
    static void setUpAll(@Autowired IUserService userService,
                         @Autowired StringRedisTemplate redisTemplate) {
        // 清理 Redis 验证码 key
        redisTemplate.delete(LOGIN_CODE_KEY + TEST_EMAIL);

        // 如果测试用户已存在则先删除
        User existUser = userService.query().eq("email", TEST_EMAIL).one();
        if (existUser != null) {
            userService.removeById(existUser.getId());
        }
    }

    @AfterAll
    static void cleanUp(@Autowired IUserService userService,
                        @Autowired StringRedisTemplate redisTemplate) {
        // 清理 Redis
        redisTemplate.delete(LOGIN_CODE_KEY + TEST_EMAIL);
        redisTemplate.delete(LOGIN_CODE_KEY + "newpass@example.com");

        // 删除测试用户
        User existUser = userService.query().eq("email", TEST_EMAIL).one();
        if (existUser != null) {
            userService.removeById(existUser.getId());
        }
        User newPassUser = userService.query().eq("email", "newpass@example.com").one();
        if (newPassUser != null) {
            userService.removeById(newPassUser.getId());
        }
    }

    @BeforeEach
    void setUp() {
        stringRedisTemplate.delete(LOGIN_CODE_KEY + TEST_EMAIL);
    }

    // ==================== 注册接口测试 ====================

    @Test
    @Order(1)
    @DisplayName("注册 - 邮箱格式无效")
    void testRegisterInvalidEmail() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"code\":\"123456\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("邮箱格式不正确"));
    }

    @Test
    @Order(2)
    @DisplayName("注册 - 密码长度不足")
    void testRegisterShortPassword() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"code\":\"123456\",\"password\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("密码长度不能少于6位"));
    }

    @Test
    @Order(3)
    @DisplayName("注册 - 验证码错误")
    void testRegisterInvalidCode() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"wrong\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("无效的验证码"));
    }

    @Test
    @Order(4)
    @DisplayName("注册 - 成功")
    void testRegisterSuccess() throws Exception {
        // 将验证码写入 Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + TEST_EMAIL, TEST_CODE, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"" + TEST_CODE + "\",\"password\":\"" + TEST_PASSWORD + "\",\"nickName\":\"" + TEST_NICKNAME + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证用户已创建
        User user = userService.query().eq("email", TEST_EMAIL).one();
        assertNotNull(user, "用户应已被创建");
        assertEquals(TEST_EMAIL, user.getEmail());
        assertEquals(TEST_NICKNAME, user.getNickName());
        assertNotNull(user.getPassword(), "密码不应为空");
        assertNotEquals(TEST_PASSWORD, user.getPassword(), "密码应被 MD5 加密");

        testUserId = user.getId();

        // 验证验证码已被删除
        assertNull(stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + TEST_EMAIL), "注册后验证码应被删除");
    }

    @Test
    @Order(5)
    @DisplayName("注册 - 邮箱已注册")
    void testRegisterDuplicateEmail() throws Exception {
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + TEST_EMAIL, TEST_CODE, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"" + TEST_CODE + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("该邮箱已注册"));
    }

    // ==================== 登录接口测试 ====================

    @Test
    @Order(6)
    @DisplayName("登录 - 邮箱格式无效")
    void testLoginInvalidEmail() throws Exception {
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("邮箱格式不正确"));
    }

    @Test
    @Order(7)
    @DisplayName("登录 - 密码为空")
    void testLoginEmptyPassword() throws Exception {
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("密码不能为空"));
    }

    @Test
    @Order(8)
    @DisplayName("登录 - 密码错误")
    void testLoginWrongPassword() throws Exception {
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("邮箱或密码错误"));
    }

    @Test
    @Order(9)
    @DisplayName("登录 - 用户不存在")
    void testLoginUserNotFound() throws Exception {
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexist@example.com\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("邮箱或密码错误"));
    }

    @Test
    @Order(10)
    @DisplayName("登录 - 成功，返回双 Token")
    void testLoginSuccess() throws Exception {
        MvcResult result = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.access_token").exists())
                .andExpect(jsonPath("$.data.refresh_token").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Result res = JSON.parseObject(body, Result.class);
        @SuppressWarnings("unchecked")
        Map<String, String> tokens = (Map<String, String>) res.getData();

        accessToken = tokens.get("access_token");
        refreshToken = tokens.get("refresh_token");

        assertNotNull(accessToken, "access_token 不应为空");
        assertNotNull(refreshToken, "refresh_token 不应为空");

        // 验证 Redis 中存储了 refresh_token -> userId
        String refreshTokenValue = stringRedisTemplate.opsForValue().get(REFRESH_TOKEN_KEY + refreshToken);
        assertNotNull(refreshTokenValue, "Redis 中应存储 refresh_token");

        // 验证 Redis 中存储了 userId -> refreshToken 映射
        Long userId = jwtUtils.getUserId(accessToken);
        String userTokenMapping = stringRedisTemplate.opsForValue().get(LOGIN_USER_TOKEN_KEY + userId);
        assertNotNull(userTokenMapping, "Redis 中应存储 userId -> refreshToken 映射");
        assertEquals(refreshToken, userTokenMapping, "userId 映射的 refreshToken 应与返回的一致");
    }

    // ==================== 受保护接口测试 ====================

    @Test
    @Order(11)
    @DisplayName("查询用户信息 - 携带有效 Token 应成功")
    void testQueryUserWithValidToken() throws Exception {
        mockMvc.perform(get("/user/" + testUserId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUserId))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.nickName").value(TEST_NICKNAME));
    }

    @Test
    @Order(12)
    @DisplayName("查询用户信息 - 无 Token 应返回 401")
    void testQueryUserWithoutToken() throws Exception {
        mockMvc.perform(get("/user/" + testUserId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
    @DisplayName("查询用户信息 - 携带无效 Token 应返回 401")
    void testQueryUserWithInvalidToken() throws Exception {
        mockMvc.perform(get("/user/" + testUserId)
                        .header("authorization", "invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(14)
    @DisplayName("查询用户信息 - 不存在的用户 ID 返回成功但无 data 字段")
    void testQueryUserNotFound() throws Exception {
        mockMvc.perform(get("/user/999999")
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ==================== 刷新 Token 接口测试 ====================

    @Test
    @Order(15)
    @DisplayName("刷新 Token - refresh_token 为空")
    void testRefreshTokenEmpty() throws Exception {
        mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("refresh_token 不能为空"));
    }

    @Test
    @Order(16)
    @DisplayName("刷新 Token - refresh_token 无效")
    void testRefreshTokenInvalid() throws Exception {
        mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("refresh_token 已过期或无效"));
    }

    @Test
    @Order(17)
    @DisplayName("刷新 Token - 成功，返回新的双 Token")
    void testRefreshTokenSuccess() throws Exception {
        String oldRefreshToken = refreshToken;

        MvcResult result = mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.access_token").exists())
                .andExpect(jsonPath("$.data.refresh_token").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Result res = JSON.parseObject(body, Result.class);
        @SuppressWarnings("unchecked")
        Map<String, String> tokens = (Map<String, String>) res.getData();

        String newAccessToken = tokens.get("access_token");
        String newRefreshToken = tokens.get("refresh_token");

        // 新旧 token 不应相同（Token 轮换）
        assertNotEquals(accessToken, newAccessToken, "新的 access_token 应与旧的不同");
        assertNotEquals(oldRefreshToken, newRefreshToken, "新的 refresh_token 应与旧的不同");

        // 旧的 refresh_token 应被删除
        assertNull(stringRedisTemplate.opsForValue().get(REFRESH_TOKEN_KEY + oldRefreshToken), "旧的 refresh_token 应被删除");

        // 新的 refresh_token 应已存储
        assertNotNull(stringRedisTemplate.opsForValue().get(REFRESH_TOKEN_KEY + newRefreshToken), "新的 refresh_token 应已存储");

        // 用新 token 更新变量供后续测试使用
        accessToken = newAccessToken;
        refreshToken = newRefreshToken;
    }

    @Test
    @Order(18)
    @DisplayName("刷新 Token - 不存在的 refresh_token 应失败")
    void testRefreshTokenNotExist() throws Exception {
        mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"non-existent-token-12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("refresh_token 已过期或无效"));
    }

    // ==================== 修改密码接口测试 ====================

    @Test
    @Order(19)
    @DisplayName("修改密码 - 邮箱格式无效")
    void testChangePasswordInvalidEmail() throws Exception {
        mockMvc.perform(post("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"code\":\"123456\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("邮箱格式不正确"));
    }

    @Test
    @Order(20)
    @DisplayName("修改密码 - 密码长度不足")
    void testChangePasswordShortPassword() throws Exception {
        mockMvc.perform(post("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"123456\",\"password\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("密码长度不能少于6位"));
    }

    @Test
    @Order(21)
    @DisplayName("修改密码 - 验证码错误")
    void testChangePasswordInvalidCode() throws Exception {
        mockMvc.perform(post("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"wrong\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("无效的验证码"));
    }

    @Test
    @Order(22)
    @DisplayName("修改密码 - 邮箱未注册")
    void testChangePasswordEmailNotRegistered() throws Exception {
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + "newpass@example.com", TEST_CODE, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        mockMvc.perform(post("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newpass@example.com\",\"code\":\"" + TEST_CODE + "\",\"password\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("该邮箱未注册"));
    }

    @Test
    @Order(23)
    @DisplayName("修改密码 - 成功")
    void testChangePasswordSuccess() throws Exception {
        String newPassword = "newpassword123";
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + TEST_EMAIL, TEST_CODE, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        mockMvc.perform(post("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"" + TEST_CODE + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证验证码已被删除
        assertNull(stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + TEST_EMAIL), "修改密码后验证码应被删除");

        // 验证可以用新密码登录
        MvcResult result = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.access_token").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Result res = JSON.parseObject(body, Result.class);
        @SuppressWarnings("unchecked")
        Map<String, String> tokens = (Map<String, String>) res.getData();
        accessToken = tokens.get("access_token");
        refreshToken = tokens.get("refresh_token");
    }

    // ==================== 登出接口测试 ====================

    @Test
    @Order(24)
    @DisplayName("登出 - 成功，清除 Redis 中的 Token")
    void testLogoutSuccess() throws Exception {
        Long userId = jwtUtils.getUserId(accessToken);

        mockMvc.perform(post("/user/logout")
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证 userId -> refreshToken 映射已被删除
        assertNull(stringRedisTemplate.opsForValue().get(LOGIN_USER_TOKEN_KEY + userId), "登出后 userId -> refreshToken 映射应被删除");

        // 验证 refresh_token 也已被删除
        assertNull(stringRedisTemplate.opsForValue().get(REFRESH_TOKEN_KEY + refreshToken), "登出后 refresh_token 应被删除");
    }

    @Test
    @Order(25)
    @DisplayName("登出 - 已登出的 refresh_token 不能再用于刷新")
    void testAccessAfterLogout() throws Exception {
        mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==================== 查询当前登录用户信息 ====================

    @Test
    @Order(26)
    @DisplayName("查询当前用户信息 - 先登录再查询")
    void testQueryMe() throws Exception {
        // 重新登录获取 token
        MvcResult loginResult = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"newpassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Result loginRes = JSON.parseObject(loginResult.getResponse().getContentAsString(), Result.class);
        @SuppressWarnings("unchecked")
        Map<String, String> tokens = (Map<String, String>) loginRes.getData();
        accessToken = tokens.get("access_token");

        mockMvc.perform(get("/user/me")
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUserId))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL));
    }

    // ==================== 修改用户昵称 ====================

    @Test
    @Order(27)
    @DisplayName("修改昵称 - 昵称为空应失败")
    void testUpdateNickNameEmpty() throws Exception {
        mockMvc.perform(put("/user")
                        .header("authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("昵称不能为空"));
    }

    @Test
    @Order(28)
    @DisplayName("修改昵称 - 成功")
    void testUpdateNickNameSuccess() throws Exception {
        String newNickName = "新昵称_" + System.currentTimeMillis();
        mockMvc.perform(put("/user")
                        .header("authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"" + newNickName + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证数据库已更新
        User user = userService.getById(testUserId);
        assertEquals(newNickName, user.getNickName(), "昵称应已更新");
    }
}
