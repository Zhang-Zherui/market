package com.market;

import com.alibaba.fastjson.JSON;
import com.market.dto.Result;
import com.market.entity.SeckillVoucher;
import com.market.entity.Voucher;
import com.market.service.ISeckillVoucherService;
import com.market.service.IVoucherService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static com.market.utils.RedisConstants.SECKILL_STOCK_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 优惠券接口集成测试
 * 覆盖: 新增普通券、新增秒杀券、更新优惠券、删除优惠券
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VoucherControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IVoucherService voucherService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static Long normalVoucherId;
    private static Long seckillVoucherId;

    @AfterAll
    static void cleanUp(@Autowired IVoucherService voucherService,
                        @Autowired ISeckillVoucherService seckillVoucherService,
                        @Autowired StringRedisTemplate redisTemplate) {
        // 删除测试优惠券及 Redis 数据
        if (normalVoucherId != null) {
            voucherService.removeById(normalVoucherId);
        }
        if (seckillVoucherId != null) {
            seckillVoucherService.removeById(seckillVoucherId);
            voucherService.removeById(seckillVoucherId);
            redisTemplate.delete(SECKILL_STOCK_KEY + seckillVoucherId);
        }
    }

    // ==================== 新增普通优惠券 ====================

    @Test
    @Order(1)
    @DisplayName("新增普通优惠券 - 成功")
    void testAddNormalVoucher() throws Exception {
        String voucherJson = "{\"title\":\"测试普通券\",\"subTitle\":\"满减券\",\"rules\":\"满100减20\","
                + "\"payValue\":10000,\"actualValue\":2000,\"type\":1,\"status\":1}";

        MvcResult result = mockMvc.perform(post("/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Result res = JSON.parseObject(body, Result.class);
        normalVoucherId = Long.valueOf(res.getData().toString());
        assertTrue(normalVoucherId > 0, "应返回有效的优惠券 ID");

        // 验证数据库中存在该优惠券
        Voucher voucher = voucherService.getById(normalVoucherId);
        assertNotNull(voucher, "优惠券应已保存到数据库");
        assertEquals("测试普通券", voucher.getTitle());
    }

    // ==================== 新增秒杀优惠券 ====================

    @Test
    @Order(2)
    @DisplayName("新增秒杀优惠券 - 成功")
    void testAddSeckillVoucher() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String voucherJson = "{\"title\":\"测试秒杀券\",\"subTitle\":\"秒杀专享\",\"rules\":\"限购一张\","
                + "\"payValue\":5000,\"actualValue\":1000,\"type\":2,\"status\":1,"
                + "\"stock\":100,\"beginTime\":\"" + now.plusDays(1) + "\",\"endTime\":\"" + now.plusDays(7) + "\"}";

        MvcResult result = mockMvc.perform(post("/voucher/seckill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Result res = JSON.parseObject(body, Result.class);
        seckillVoucherId = Long.valueOf(res.getData().toString());
        assertTrue(seckillVoucherId > 0, "应返回有效的优惠券 ID");

        // 验证数据库中存在该优惠券
        Voucher voucher = voucherService.getById(seckillVoucherId);
        assertNotNull(voucher, "秒杀券应已保存到数据库");
        assertEquals("测试秒杀券", voucher.getTitle());

        // 验证秒杀信息表
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(seckillVoucherId);
        assertNotNull(seckillVoucher, "秒杀信息应已保存到数据库");
        assertEquals(100, seckillVoucher.getStock());

        // 验证 Redis 中已写入库存
        String stockInRedis = stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + seckillVoucherId);
        assertNotNull(stockInRedis, "Redis 中应已写入库存");
        assertEquals("100", stockInRedis);
    }

    // ==================== 更新优惠券 ====================

    @Test
    @Order(3)
    @DisplayName("更新优惠券 - 成功")
    void testUpdateVoucher() throws Exception {
        assertNotNull(normalVoucherId, "普通优惠券 ID 不应为空");

        String updateJson = "{\"id\":" + normalVoucherId + ",\"title\":\"更新后的优惠券\","
                + "\"subTitle\":\"满减券\",\"rules\":\"满100减30\","
                + "\"payValue\":10000,\"actualValue\":3000,\"type\":1,\"status\":1}";

        mockMvc.perform(put("/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证数据库已更新
        Voucher voucher = voucherService.getById(normalVoucherId);
        assertNotNull(voucher);
        assertEquals("更新后的优惠券", voucher.getTitle());
        assertEquals(3000L, voucher.getActualValue());
    }

    // ==================== 删除优惠券 ====================

    @Test
    @Order(4)
    @DisplayName("删除普通优惠券 - 成功")
    void testDeleteNormalVoucher() throws Exception {
        assertNotNull(normalVoucherId, "普通优惠券 ID 不应为空");

        mockMvc.perform(delete("/voucher/" + normalVoucherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证数据库中已删除
        Voucher voucher = voucherService.getById(normalVoucherId);
        assertNull(voucher, "优惠券应已从数据库中删除");
        normalVoucherId = null; // 避免重复删除
    }

    @Test
    @Order(5)
    @DisplayName("删除秒杀优惠券 - 成功，同时删除秒杀信息")
    void testDeleteSeckillVoucher() throws Exception {
        assertNotNull(seckillVoucherId, "秒杀优惠券 ID 不应为空");

        mockMvc.perform(delete("/voucher/" + seckillVoucherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证优惠券已删除
        assertNull(voucherService.getById(seckillVoucherId), "优惠券应已从数据库中删除");

        // 验证秒杀信息也已删除
        assertNull(seckillVoucherService.getById(seckillVoucherId), "秒杀信息应已从数据库中删除");
        seckillVoucherId = null;
    }

    // ==================== 查询优惠券列表 ====================

    @Test
    @Order(6)
    @DisplayName("查询优惠券列表 - 无参数返回全部")
    void testQueryVoucherListAll() throws Exception {
        mockMvc.perform(get("/voucher/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(7)
    @DisplayName("查询优惠券列表 - 按类型筛选")
    void testQueryVoucherListByType() throws Exception {
        mockMvc.perform(get("/voucher/list?type=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(8)
    @DisplayName("查询优惠券列表 - 按状态筛选")
    void testQueryVoucherListByStatus() throws Exception {
        mockMvc.perform(get("/voucher/list?status=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(9)
    @DisplayName("查询优惠券列表 - 按类型和状态同时筛选")
    void testQueryVoucherListByTypeAndStatus() throws Exception {
        mockMvc.perform(get("/voucher/list?type=0&status=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== 查询优惠券详情 ====================

    @Test
    @Order(10)
    @DisplayName("查询优惠券详情 - 不存在返回失败")
    void testQueryVoucherByIdNotFound() throws Exception {
        mockMvc.perform(get("/voucher/999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("优惠券不存在"));
    }

    @Test
    @Order(11)
    @DisplayName("查询优惠券详情 - 普通券成功")
    void testQueryNormalVoucherById() throws Exception {
        // 先创建一个普通券
        String voucherJson = "{\"title\":\"详情测试普通券\",\"subTitle\":\"测试\",\"rules\":\"无\","
                + "\"payValue\":1000,\"actualValue\":500,\"type\":0,\"status\":1}";
        MvcResult createResult = mockMvc.perform(post("/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Result res = JSON.parseObject(createResult.getResponse().getContentAsString(), Result.class);
        Long voucherId = Long.valueOf(res.getData().toString());

        try {
            mockMvc.perform(get("/voucher/" + voucherId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("详情测试普通券"))
                    .andExpect(jsonPath("$.data.type").value(0));
        } finally {
            voucherService.removeById(voucherId);
        }
    }

    @Test
    @Order(12)
    @DisplayName("查询优惠券详情 - 秒杀券包含库存和时间")
    void testQuerySeckillVoucherById() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String voucherJson = "{\"title\":\"详情测试秒杀券\",\"subTitle\":\"测试\",\"rules\":\"无\","
                + "\"payValue\":1000,\"actualValue\":500,\"type\":1,\"status\":1,"
                + "\"stock\":50,\"beginTime\":\"" + now.plusDays(1) + "\",\"endTime\":\"" + now.plusDays(7) + "\"}";

        MvcResult createResult = mockMvc.perform(post("/voucher/seckill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Result res = JSON.parseObject(createResult.getResponse().getContentAsString(), Result.class);
        Long voucherId = Long.valueOf(res.getData().toString());

        try {
            mockMvc.perform(get("/voucher/" + voucherId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("详情测试秒杀券"))
                    .andExpect(jsonPath("$.data.stock").value(50))
                    .andExpect(jsonPath("$.data.beginTime").exists())
                    .andExpect(jsonPath("$.data.endTime").exists());
        } finally {
            seckillVoucherService.removeById(voucherId);
            voucherService.removeById(voucherId);
            stringRedisTemplate.delete(SECKILL_STOCK_KEY + voucherId);
        }
    }

    // ==================== 优惠券上下架 ====================

    @Test
    @Order(13)
    @DisplayName("优惠券上下架 - 成功")
    void testUpdateVoucherStatus() throws Exception {
        // 创建测试券
        String voucherJson = "{\"title\":\"上下架测试券\",\"subTitle\":\"测试\",\"rules\":\"无\","
                + "\"payValue\":1000,\"actualValue\":500,\"type\":0,\"status\":1}";
        MvcResult createResult = mockMvc.perform(post("/voucher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Result res = JSON.parseObject(createResult.getResponse().getContentAsString(), Result.class);
        Long voucherId = Long.valueOf(res.getData().toString());

        try {
            // 下架
            mockMvc.perform(put("/voucher/" + voucherId + "/status?status=2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            Voucher v = voucherService.getById(voucherId);
            assertEquals(2, v.getStatus(), "状态应变为下架");

            // 重新上架
            mockMvc.perform(put("/voucher/" + voucherId + "/status?status=1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            v = voucherService.getById(voucherId);
            assertEquals(1, v.getStatus(), "状态应变为上架");
        } finally {
            voucherService.removeById(voucherId);
        }
    }
}
