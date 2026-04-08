package com.market.controller;


import com.market.dto.Result;
import com.market.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Autowired
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 查询当前用户拥有的所有代金券订单
     */
    @GetMapping("/my")
    public Result queryMyVouchers() {
        return voucherOrderService.queryMyVouchers();
    }

    /**
     * 查询单个订单详情
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable("id") Long id) {
        return voucherOrderService.queryOrderById(id);
    }

    /**
     * 普通券购买（非秒杀券）
     * @param id 优惠券ID
     * @return 订单ID
     */
    @PostMapping("/{id}")
    public Result buyVoucher(@PathVariable("id") Long id) {
        return voucherOrderService.buyVoucher(id);
    }

    /**
     * 订单支付
     * @param id 订单ID
     * @param payType 支付方式（1余额/2支付宝/3微信）
     * @return 操作结果
     */
    @PutMapping("/{id}/pay")
    public Result payOrder(@PathVariable("id") Long id, @RequestParam("payType") Integer payType) {
        return voucherOrderService.payOrder(id, payType);
    }

    /**
     * 取消订单
     * @param id 订单ID
     * @return 操作结果
     */
    @PutMapping("/{id}/cancel")
    public Result cancelOrder(@PathVariable("id") Long id) {
        return voucherOrderService.cancelOrder(id);
    }

    /**
     * 核销订单
     * @param id 订单ID
     * @return 操作结果
     */
    @PutMapping("/{id}/use")
    public Result useOrder(@PathVariable("id") Long id) {
        return voucherOrderService.useOrder(id);
    }

    /**
     * 申请退款
     * @param id 订单ID
     * @return 操作结果
     */
    @PutMapping("/{id}/refund")
    public Result refundOrder(@PathVariable("id") Long id) {
        return voucherOrderService.refundOrder(id);
    }

    /**
     * 确认退款
     * @param id 订单ID
     * @return 操作结果
     */
    @PutMapping("/{id}/refund/confirm")
    public Result confirmRefund(@PathVariable("id") Long id) {
        return voucherOrderService.confirmRefund(id);
    }
}
