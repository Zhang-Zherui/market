package com.market.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.RateLimiter;
import com.market.dto.Result;
import com.market.dto.VoucherOrderVO;
import com.market.entity.Voucher;
import com.market.entity.VoucherOrder;
import com.market.mapper.VoucherOrderMapper;
import com.market.rebbitmq.MQSender;
import com.market.service.IVoucherOrderService;
import com.market.service.IVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private MQSender mqSender;

    private RateLimiter rateLimiter=RateLimiter.create(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IVoucherService voucherService;

    //lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //令牌桶算法 限流
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)){
            return Result.fail("目前网络正忙，请重试");
        }
        //1.执行lua脚本
        Long userId = UserHolder.getUser().getId();

        Long r = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //2.判断结果为0
        int result = r.intValue();
        if (result != 0) {
            //2.1不为0代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "该用户重复下单");
        }
        //2.2为0代表有购买资格,将下单信息保存到阻塞队列

        //2.3创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.4订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //2.5用户id
        voucherOrder.setUserId(userId);
        //2.6代金卷id
        voucherOrder.setVoucherId(voucherId);

        //2.7将信息放入MQ中
        mqSender.sendSeckillMessage(JSON.toJSONString(voucherOrder));


        //2.7 返回订单id
        return Result.ok(orderId);
//        单机模式下，使用synchronized实现锁
//        synchronized (userId.toString().intern())
//        {
//            //    createVoucherOrder的事物不会生效,因为你调用的方法，其实是this.的方式调用的，事务想要生效，
//            //    还得利用代理来生效，所以这个地方，我们需要获得原始的事务对象， 来操作事务
//            return voucherOrderService.createVoucherOrder(voucherId);
//        }
    }


//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 一人一单逻辑
//        Long userId = UserHolder.getUser().getId();
//
//
//        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
//        if (count > 0){
//            return Result.fail("你已经抢过优惠券了哦");
//        }
//
//        //5. 扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock",0)   //加了CAS 乐观锁，Compare and swap
//                .update();
//
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//
////        库存足且在时间范围内的，则创建新的订单
//        //6. 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //6.1 设置订单id，生成订单的全局id
//        long orderId = redisIdWorker.nextId("order");
//        //6.2 设置用户id
//        Long id = UserHolder.getUser().getId();
//        //6.3 设置代金券id
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(id);
//        //7. 将订单数据保存到表中
//        save(voucherOrder);
//        //8. 返回订单id
//        return Result.ok(orderId);
//    }

    @Override
    public Result queryMyVouchers() {
        Long userId = UserHolder.getUser().getId();
        List<VoucherOrder> orders = query().eq("user_id", userId).orderByDesc("create_time").list();
        if (orders.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 批量查询关联的优惠券
        List<Long> voucherIds = orders.stream().map(VoucherOrder::getVoucherId).distinct().collect(Collectors.toList());
        List<Voucher> vouchers = voucherService.listByIds(voucherIds);
        Map<Long, Voucher> voucherMap = vouchers.stream().collect(Collectors.toMap(Voucher::getId, v -> v));

        // 组装 VO
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
            Voucher v = voucherMap.get(order.getVoucherId());
            if (v != null) {
                vo.setVoucherTitle(v.getTitle());
                vo.setVoucherSubTitle(v.getSubTitle());
                vo.setPayValue(v.getPayValue());
                vo.setActualValue(v.getActualValue());
                vo.setVoucherType(v.getType());
                vo.setVoucherRules(v.getRules());
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
        // 1. 查询优惠券
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
        // 2. 创建订单
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
}

