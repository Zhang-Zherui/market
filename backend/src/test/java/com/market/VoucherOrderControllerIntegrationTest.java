package com.market;

import com.alibaba.fastjson.JSON;
import com.market.dto.Result;
import com.market.entity.SeckillVoucher;
import com.market.entity.User;
import com.market.entity.Voucher;
import com.market.entity.VoucherOrder;
import com.market.rebbitmq.MQSender;
import com.market.service.ISeckillVoucherService;
import com.market.service.IUserService;
import com.market.service.IVoucherOrderService;
import com.market.service.IVoucherService;
import com.market.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.market.utils.RedisConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 秒杀下单接口集成测试
 * 覆盖: 秒杀下单成功、库存不足、重复下单
 * 使用 @MockBean 模拟 MQSender，避免依赖 RabbitMQ 服务
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VoucherOrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private IUserService userService;

    @Autowired
    private IVoucherService voucherService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @MockBean
    private MQSender mqSender;

    private static final String TEST_EMAIL = "seckill_test@example.com";
    private static final String TEST_PASSWORD = "seckill123456";
    private static final String TEST_CODE = "654321";
    private static final String TEST_NICKNAME = "秒杀测试用户";

    private static String accessToken;
    private static Long testUserId;
    private static Long seckillVoucherId;
    private static final int TEST_STOCK = 10;

    private static Long normalVoucherId;
    private static Long normalOrderId;

    @BeforeAll
    static void setUpAll(@Autowired IUserService userService) {
        // 清理可能残留的测试用户
        User existUser = userService.query().eq("email", TEST_EMAIL).one();
        if (existUser != null) {
            userService.removeById(existUser.getId());
        }
    }

    @AfterAll
    static void cleanUp(@Autowired IUserService userService,
                        @Autowired IVoucherService voucherService,
                        @Autowired ISeckillVoucherService seckillVoucherService,
                        @Autowired IVoucherOrderService voucherOrderService,
                        @Autowired StringRedisTemplate redisTemplate) {
        // 清理 Redis
        redisTemplate.delete(LOGIN_CODE_KEY + TEST_EMAIL);
        if (testUserId != null) {
            redisTemplate.delete(LOGIN_USER_TOKEN_KEY + testUserId);
        }
        if (seckillVoucherId != null) {
            redisTemplate.delete(SECKILL_STOCK_KEY + seckillVoucherId);
            redisTemplate.delete("seckill:order:" + seckillVoucherId);
        }

        // 删除测试用户
        User existUser = userService.query().eq("email", TEST_EMAIL).one();
        if (existUser != null) {
            userService.removeById(existUser.getId());
        }
        // 删除测试优惠券
        if (seckillVoucherId != null) {
            seckillVoucherService.removeById(seckillVoucherId);
            voucherService.removeById(seckillVoucherId);
        }
        // 删除普通券测试数据
        if (normalOrderId != null) {
            voucherOrderService.removeById(normalOrderId);
        }
        if (normalVoucherId != null) {
            voucherService.removeById(normalVoucherId);
        }
    }

    /**
     * 注册测试用户 + 创建秒杀券
     */
    @Test
    @Order(0)
    @DisplayName("初始化 - 注册测试用户并创建秒杀券")
    void setUpTestData() throws Exception {
        // 1. 注册测试用户
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + TEST_EMAIL, TEST_CODE, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"" + TEST_CODE + "\",\"password\":\"" + TEST_PASSWORD + "\",\"nickName\":\"" + TEST_NICKNAME + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User user = userService.query().eq("email", TEST_EMAIL).one();
        assertNotNull(user);
        testUserId = user.getId();

        // 2. 登录获取 token
        MvcResult loginResult = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Result res = JSON.parseObject(loginResult.getResponse().getContentAsString(), Result.class);
        @SuppressWarnings("unchecked")
        Map<String, String> tokens = (Map<String, String>) res.getData();
        accessToken = tokens.get("access_token");

        // 3. 创建秒杀券
        LocalDateTime now = LocalDateTime.now();
        com.market.entity.Voucher voucher = new com.market.entity.Voucher();
        voucher.setTitle("测试秒杀券");
        voucher.setSubTitle("秒杀专享");
        voucher.setRules("限购一张");
        voucher.setPayValue(5000L);
        voucher.setActualValue(1000L);
        voucher.setType(2);
        voucher.setStatus(1);
        voucher.setStock(TEST_STOCK);
        voucher.setBeginTime(now.plusDays(1));
        voucher.setEndTime(now.plusDays(7));
        voucherService.addSeckillVoucher(voucher);

        seckillVoucherId = voucher.getId();
        assertNotNull(seckillVoucherId);

        // 验证 Redis 库存
        String stockInRedis = stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + seckillVoucherId);
        assertEquals(String.valueOf(TEST_STOCK), stockInRedis, "Redis 库存应正确");
    }

    // ==================== 秒杀下单测试 ====================

    @Test
    @Order(1)
    @DisplayName("秒杀下单 - 未登录应返回 401")
    void testSeckillWithoutToken() throws Exception {
        mockMvc.perform(post("/voucher-order/seckill/" + seckillVoucherId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("秒杀下单 - 成功")
    void testSeckillSuccess() throws Exception {
        // Mock MQ 发送
        doNothing().when(mqSender).sendSeckillMessage(anyString());

        MvcResult result = mockMvc.perform(post("/voucher-order/seckill/" + seckillVoucherId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        // 验证返回了订单 ID
        String body = result.getResponse().getContentAsString();
        Result res = JSON.parseObject(body, Result.class);
        Long orderId = Long.valueOf(res.getData().toString());
        assertTrue(orderId > 0, "应返回有效的订单 ID");

        // 验证 Redis 库存已扣减
        String stockInRedis = stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + seckillVoucherId);
        assertEquals(String.valueOf(TEST_STOCK - 1), stockInRedis, "库存应扣减 1");

        // 验证用户已在订单集合中（一人一单）
        Boolean isMember = stringRedisTemplate.opsForSet()
                .isMember("seckill:order:" + seckillVoucherId, testUserId.toString());
        assertTrue(Boolean.TRUE.equals(isMember), "用户应在订单集合中");
    }

    @Test
    @Order(3)
    @DisplayName("秒杀下单 - 重复下单应失败")
    void testSeckillDuplicate() throws Exception {
        mockMvc.perform(post("/voucher-order/seckill/" + seckillVoucherId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("该用户重复下单"));
    }

    @Test
    @Order(4)
    @DisplayName("秒杀下单 - 库存不足应失败")
    void testSeckillOutOfStock() throws Exception {
        // 将 Redis 库存设为 0
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + seckillVoucherId, "0");

        mockMvc.perform(post("/voucher-order/seckill/" + seckillVoucherId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("库存不足"));

        // 恢复库存（为了后续 cleanUp 不出问题）
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + seckillVoucherId, String.valueOf(TEST_STOCK));
    }

    // ==================== 普通券购买测试 ====================

    @Test
    @Order(5)
    @DisplayName("普通券购买 - 未登录应返回 401")
    void testBuyVoucherWithoutToken() throws Exception {
        // 先创建一个普通券
        Voucher v = new Voucher();
        v.setTitle("普通券购买测试");
        v.setSubTitle("测试");
        v.setRules("无");
        v.setPayValue(1000L);
        v.setActualValue(500L);
        v.setType(0);
        v.setStatus(1);
        voucherService.save(v);
        normalVoucherId = v.getId();

        try {
            mockMvc.perform(post("/voucher-order/" + normalVoucherId))
                    .andExpect(status().isUnauthorized());
        } finally {
            voucherService.removeById(normalVoucherId);
            normalVoucherId = null;
        }
    }

    @Test
    @Order(6)
    @DisplayName("普通券购买 - 秒杀券不能通过此接口购买")
    void testBuyVoucherWithSeckillType() throws Exception {
        // 秒杀券已在 Order0 创建，直接使用
        mockMvc.perform(post("/voucher-order/" + seckillVoucherId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("该优惠券为秒杀券，请通过秒杀渠道购买"));
    }

    @Test
    @Order(7)
    @DisplayName("普通券购买 - 成功")
    void testBuyVoucherSuccess() throws Exception {
        // 创建普通券
        Voucher v = new Voucher();
        v.setTitle("普通券购买成功测试");
        v.setSubTitle("测试");
        v.setRules("无");
        v.setPayValue(1000L);
        v.setActualValue(500L);
        v.setType(0);
        v.setStatus(1);
        voucherService.save(v);
        normalVoucherId = v.getId();

        MvcResult result = mockMvc.perform(post("/voucher-order/" + normalVoucherId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        Result res = JSON.parseObject(result.getResponse().getContentAsString(), Result.class);
        normalOrderId = Long.valueOf(res.getData().toString());
        assertTrue(normalOrderId > 0, "应返回有效的订单 ID");

        // 验证数据库中存在该订单
        VoucherOrder order = voucherOrderService.getById(normalOrderId);
        assertNotNull(order, "订单应已保存到数据库");
        assertEquals(testUserId, order.getUserId());
        assertEquals(normalVoucherId, order.getVoucherId());
        assertEquals(1, order.getStatus(), "订单状态应为未支付");
    }

    // ==================== 查询订单详情 ====================

    @Test
    @Order(8)
    @DisplayName("查询订单详情 - 不存在应失败")
    void testQueryOrderByIdNotFound() throws Exception {
        mockMvc.perform(get("/voucher-order/999999")
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("订单不存在"));
    }

    @Test
    @Order(9)
    @DisplayName("查询订单详情 - 成功")
    void testQueryOrderByIdSuccess() throws Exception {
        assertNotNull(normalOrderId, "订单 ID 不应为空，请确保前面的测试已执行");

        mockMvc.perform(get("/voucher-order/" + normalOrderId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(normalOrderId))
                .andExpect(jsonPath("$.data.voucherId").value(normalVoucherId))
                .andExpect(jsonPath("$.data.status").value(1));
    }

    // ==================== 订单支付测试 ====================

    @Test
    @Order(10)
    @DisplayName("订单支付 - 未登录应返回 401")
    void testPayWithoutToken() throws Exception {
        mockMvc.perform(put("/voucher-order/" + normalOrderId + "/pay?payType=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    @DisplayName("订单支付 - 成功，状态变为已支付")
    void testPaySuccess() throws Exception {
        assertNotNull(normalOrderId, "订单 ID 不应为空");

        mockMvc.perform(put("/voucher-order/" + normalOrderId + "/pay?payType=2")
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        VoucherOrder order = voucherOrderService.getById(normalOrderId);
        assertEquals(2, order.getStatus(), "状态应变为已支付");
        assertEquals(2, order.getPayType(), "支付方式应被设置");
        assertNotNull(order.getPayTime(), "支付时间应被记录");
    }

    @Test
    @Order(12)
    @DisplayName("订单支付 - 已支付的订单再次支付应失败")
    void testPayAlreadyPaid() throws Exception {
        mockMvc.perform(put("/voucher-order/" + normalOrderId + "/pay?payType=1")
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("订单状态异常，无法支付"));
    }

    // ==================== 核销订单测试 ====================

    @Test
    @Order(13)
    @DisplayName("核销订单 - 成功，状态变为已核销")
    void testUseOrderSuccess() throws Exception {
        mockMvc.perform(put("/voucher-order/" + normalOrderId + "/use")
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        VoucherOrder order = voucherOrderService.getById(normalOrderId);
        assertEquals(3, order.getStatus(), "状态应变为已核销");
        assertNotNull(order.getUseTime(), "核销时间应被记录");
    }

    // ==================== 订单取消测试 ====================

    @Test
    @Order(14)
    @DisplayName("取消订单 - 创建新订单并取消")
    void testCancelOrder() throws Exception {
        // 创建一个新的普通券订单用于取消测试
        Voucher v = new Voucher();
        v.setTitle("取消测试券");
        v.setSubTitle("测试");
        v.setRules("无");
        v.setPayValue(1000L);
        v.setActualValue(500L);
        v.setType(0);
        v.setStatus(1);
        voucherService.save(v);
        Long cancelVoucherId = v.getId();

        MvcResult result = mockMvc.perform(post("/voucher-order/" + cancelVoucherId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Result res = JSON.parseObject(result.getResponse().getContentAsString(), Result.class);
        Long cancelOrderId = Long.valueOf(res.getData().toString());

        try {
            // 取消订单
            mockMvc.perform(put("/voucher-order/" + cancelOrderId + "/cancel")
                            .header("authorization", accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            VoucherOrder order = voucherOrderService.getById(cancelOrderId);
            assertEquals(4, order.getStatus(), "状态应变为已取消");

            // 已取消的订单不能再次取消
            mockMvc.perform(put("/voucher-order/" + cancelOrderId + "/cancel")
                            .header("authorization", accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            voucherOrderService.removeById(cancelOrderId);
            voucherService.removeById(cancelVoucherId);
        }
    }

    // ==================== 退款测试 ====================

    @Test
    @Order(15)
    @DisplayName("申请退款 - 已支付的订单申请退款成功")
    void testRefundOrder() throws Exception {
        // 创建新的券和已支付订单
        Voucher v = new Voucher();
        v.setTitle("退款测试券");
        v.setSubTitle("测试");
        v.setRules("无");
        v.setPayValue(1000L);
        v.setActualValue(500L);
        v.setType(0);
        v.setStatus(1);
        voucherService.save(v);
        Long refundVoucherId = v.getId();

        MvcResult result = mockMvc.perform(post("/voucher-order/" + refundVoucherId)
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Result res = JSON.parseObject(result.getResponse().getContentAsString(), Result.class);
        Long refundOrderId = Long.valueOf(res.getData().toString());

        // 先支付
        mockMvc.perform(put("/voucher-order/" + refundOrderId + "/pay?payType=1")
                        .header("authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        try {
            // 申请退款
            mockMvc.perform(put("/voucher-order/" + refundOrderId + "/refund")
                            .header("authorization", accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            VoucherOrder order = voucherOrderService.getById(refundOrderId);
            assertEquals(5, order.getStatus(), "状态应变为退款中");

            // 确认退款
            mockMvc.perform(put("/voucher-order/" + refundOrderId + "/refund/confirm")
                            .header("authorization", accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            order = voucherOrderService.getById(refundOrderId);
            assertEquals(6, order.getStatus(), "状态应变为已退款");
            assertNotNull(order.getRefundTime(), "退款时间应被记录");
        } finally {
            voucherOrderService.removeById(refundOrderId);
            voucherService.removeById(refundVoucherId);
        }
    }
}
