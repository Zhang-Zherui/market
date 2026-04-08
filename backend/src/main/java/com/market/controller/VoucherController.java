package com.market.controller;


import com.market.dto.Result;
import com.market.entity.Voucher;
import com.market.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 更新优惠券信息
     * 只更新MySQL，Canal会自动同步Redis缓存
     * @param voucher 优惠券信息
     * @return 操作结果
     */
    @PutMapping
    public Result updateVoucher(@RequestBody Voucher voucher) {
        voucherService.updateVoucher(voucher);
        return Result.ok();
    }

    /**
     * 删除优惠券
     * 只删除MySQL，Canal会自动清理Redis缓存
     * @param id 优惠券ID
     * @return 操作结果
     */
    @DeleteMapping("{id}")
    public Result deleteVoucher(@PathVariable("id") Long id) {
        voucherService.deleteVoucher(id);
        return Result.ok();
    }

    /**
     * 配置秒杀优惠券库存数量
     * @param voucherId 优惠券ID
     * @param stock 新的库存数量
     * @return 操作结果
     */
    @PutMapping("stock/{id}")
    public Result updateStock(@PathVariable("id") Long voucherId, @RequestParam("stock") Integer stock) {
        voucherService.updateStock(voucherId, stock);
        return Result.ok();
    }

    /**
     * 查询优惠券列表
     * @param type 优惠券类型（0普通/1秒杀），可选
     * @param status 优惠券状态（1上架/2下架/3过期），可选
     * @return 优惠券列表
     */
    @GetMapping("/list")
    public Result queryVoucherList(
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "status", required = false) Integer status) {
        return voucherService.queryVoucherList(type, status);
    }

    /**
     * 查询优惠券详情
     * @param id 优惠券ID
     * @return 优惠券详情
     */
    @GetMapping("/{id}")
    public Result queryVoucherById(@PathVariable("id") Long id) {
        return voucherService.queryVoucherById(id);
    }

    /**
     * 优惠券上下架
     * @param id 优惠券ID
     * @param status 状态（1上架/2下架/3过期）
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result updateVoucherStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        Voucher voucher = new Voucher();
        voucher.setId(id);
        voucher.setStatus(status);
        voucherService.updateVoucher(voucher);
        return Result.ok();
    }
}
