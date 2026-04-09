package com.market.service;

import com.market.dto.Result;
import com.market.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    /**
     * 查询当前用户拥有的所有代金券订单
     */
    Result queryMyVouchers();

    /**
     * 查询单个订单详情
     * @param id 订单ID
     * @return 订单详情
     */
    Result queryOrderById(Long id);

    /**
     * 订单支付（未支付->已支付）
     * @param id 订单ID
     * @param payType 支付方式（1余额/2支付宝/3微信）
     * @return 操作结果
     */
    Result payOrder(Long id, Integer payType);

    /**
     * 取消订单（未支付->已取消）
     * @param id 订单ID
     * @return 操作结果
     */
    Result cancelOrder(Long id);

    /**
     * 核销订单（已支付->已核销）
     * @param id 订单ID
     * @return 操作结果
     */
    Result useOrder(Long id);

    /**
     * 申请退款（已支付->退款中）
     * @param id 订单ID
     * @return 操作结果
     */
    Result refundOrder(Long id);

    /**
     * 确认退款（退款中->已退款）
     * @param id 订单ID
     * @return 操作结果
     */
    Result confirmRefund(Long id);

    /**
     * 普通券购买（非秒杀券）
     * @param voucherId 优惠券ID
     * @return 操作结果
     */
    Result buyVoucher(Long voucherId);

    /**
     * 扫描已支付但支付超时的订单，直接标记为已退款
     * @return 本次处理的订单数
     */
    int expirePaidOrders();

}
