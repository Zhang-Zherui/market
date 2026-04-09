package com.market.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.market.annotation.RateLimit;
import com.market.dto.Result;
import com.market.dto.VoucherOrderVO;
import com.market.entity.Voucher;
import com.market.entity.VoucherOrder;
import com.market.mapper.VoucherOrderMapper;
import com.market.rebbitmq.MQSender;
import com.market.service.IVoucherOrderService;
import com.market.service.IVoucherService;
import com.market.utils.RedisIdWorker;
import com.market.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private MQSender mqSender;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IVoucherService voucherService;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    @RateLimit(
            name = "voucher-order-seckill",
            permitsPerSecond = 10,
            timeoutMs = 1000,
            message = "当前网络正忙，请稍后重试"
    )
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        Long resultValue = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );

        int result = resultValue.intValue();
        if (result != 0) {
            return Result.fail(result == 1 ? "库存不足" : "该用户重复下单");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        mqSender.sendSeckillMessage(JSON.toJSONString(voucherOrder));
        return Result.ok(orderId);
    }

    @Override
    public Result queryMyVouchers() {
        Long userId = UserHolder.getUser().getId();
        List<VoucherOrder> orders = query().eq("user_id", userId).orderByDesc("create_time").list();
        if (orders.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> voucherIds = orders.stream()
                .map(VoucherOrder::getVoucherId)
                .distinct()
                .collect(Collectors.toList());
        List<Voucher> vouchers = voucherService.listByIds(voucherIds);
        Map<Long, Voucher> voucherMap = vouchers.stream().collect(Collectors.toMap(Voucher::getId, v -> v));

        List<VoucherOrderVO> voList = new ArrayList<>();
        for (VoucherOrder order : orders) {
            VoucherOrderVO vo = new VoucherOrderVO();
            vo.setId(order.getId());
            vo.setVoucherId(order.getVoucherId());
            vo.setPayType(order.getPayType());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());
            vo.setPayTime(order.getPayTime());
            vo.setUseTime(order.getUseTime());
            vo.setRefundTime(order.getRefundTime());
            vo.setUpdateTime(order.getUpdateTime());

            Voucher voucher = voucherMap.get(order.getVoucherId());
            if (voucher != null) {
                vo.setVoucherTitle(voucher.getTitle());
                vo.setVoucherSubTitle(voucher.getSubTitle());
                vo.setPayValue(voucher.getPayValue());
                vo.setActualValue(voucher.getActualValue());
                vo.setVoucherType(voucher.getType());
                vo.setVoucherRules(voucher.getRules());
            }
            voList.add(vo);
        }
        return Result.ok(voList);
    }

    @Override
    public Result queryOrderById(Long id) {
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        return Result.ok(order);
    }

    @Override
    @Transactional
    public Result payOrder(Long id, Integer payType) {
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 1) {
            return Result.fail("订单状态异常，无法支付");
        }
        update().eq("id", id)
                .set("status", 2)
                .set("pay_type", payType)
                .set("pay_time", LocalDateTime.now())
                .update();
        return Result.ok();
    }

    @Override
    @Transactional
    public Result cancelOrder(Long id) {
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 1) {
            return Result.fail("只有未支付的订单才能取消");
        }
        update().eq("id", id)
                .set("status", 4)
                .update();
        return Result.ok();
    }

    @Override
    @Transactional
    public Result useOrder(Long id) {
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 2) {
            return Result.fail("只有已支付的订单才能核销");
        }
        update().eq("id", id)
                .set("status", 3)
                .set("use_time", LocalDateTime.now())
                .update();
        return Result.ok();
    }

    @Override
    @Transactional
    public Result refundOrder(Long id) {
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 2) {
            return Result.fail("只有已支付的订单才能申请退款");
        }
        update().eq("id", id)
                .set("status", 5)
                .update();
        return Result.ok();
    }

    @Override
    @Transactional
    public Result confirmRefund(Long id) {
        VoucherOrder order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != 5) {
            return Result.fail("只有退款中的订单才能确认退款");
        }
        update().eq("id", id)
                .set("status", 6)
                .set("refund_time", LocalDateTime.now())
                .update();
        return Result.ok();
    }

    @Override
    @Transactional
    public Result buyVoucher(Long voucherId) {
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        if (voucher.getType() != 0) {
            return Result.fail("该优惠券为秒杀券，请通过秒杀渠道购买");
        }
        if (voucher.getStatus() != 1) {
            return Result.fail("该优惠券未上架");
        }

        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayType(1);
        order.setStatus(1);
        save(order);
        return Result.ok(order.getId());
    }

    @Override
    @Transactional
    public int expirePaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusHours(1);

        UpdateWrapper<VoucherOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("status", 2)
                .lt("pay_time", deadline)
                .set("status", 6)
                .set("refund_time", now);

        return baseMapper.update(null, wrapper);
    }
}
