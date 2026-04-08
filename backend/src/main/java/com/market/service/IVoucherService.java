package com.market.service;

import com.market.dto.Result;
import com.market.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherService extends IService<Voucher> {

    void addSeckillVoucher(Voucher voucher);

    /**
     * 更新优惠券信息（只更新MySQL，Canal会自动同步缓存）
     * @param voucher 优惠券信息
     */
    void updateVoucher(Voucher voucher);

    /**
     * 删除优惠券（只删除MySQL，Canal会自动清理缓存）
     * @param voucherId 优惠券ID
     */
    void deleteVoucher(Long voucherId);

    /**
     * 更新秒杀优惠券库存数量
     * @param voucherId 优惠券ID
     * @param stock 新的库存数量
     */
    void updateStock(Long voucherId, Integer stock);

    /**
     * 查询优惠券列表（支持按类型和状态筛选）
     * @param type 优惠券类型（0普通/1秒杀），可选
     * @param status 优惠券状态（1上架/2下架/3过期），可选
     * @return 优惠券列表
     */
    Result queryVoucherList(Integer type, Integer status);

    /**
     * 查询优惠券详情，秒杀券关联查询库存和时间
     * @param id 优惠券ID
     * @return 优惠券详情
     */
    Result queryVoucherById(Long id);
}
