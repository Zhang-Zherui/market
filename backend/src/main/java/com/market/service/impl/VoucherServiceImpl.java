package com.market.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.market.dto.Result;
import com.market.entity.SeckillVoucher;
import com.market.entity.Voucher;
import com.market.mapper.VoucherMapper;
import com.market.service.ISeckillVoucherService;
import com.market.service.IVoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.market.utils.RedisConstants.SECKILL_STOCK_KEY;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券到普通优惠券voucher数据库
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        //把秒杀信息写入缓存，否则执行seckill.lua的时候找不到缓存，导致与空值比较从而报错
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
        seckillVoucherService.save(seckillVoucher);
        
        
    }

    @Override
    @Transactional
    public void updateVoucher(Voucher voucher) {
        // 只更新MySQL，Canal会自动同步Redis缓存
        updateById(voucher);
    }

    @Override
    @Transactional
    public void deleteVoucher(Long voucherId) {
        // 只删除MySQL，Canal会自动清理Redis缓存
        removeById(voucherId);
        // 同时删除秒杀信息
        seckillVoucherService.removeById(voucherId);
    }

    @Override
    @Transactional
    public void updateStock(Long voucherId, Integer stock) {
        // 更新MySQL中的库存
        seckillVoucherService.update()
                .setSql("stock = " + stock)
                .eq("voucher_id", voucherId)
                .update();
        // 同步更新Redis秒杀库存缓存
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucherId, stock.toString());
    }

    @Override
    public Result queryVoucherList(Integer type, Integer status) {
        List<Voucher> vouchers = query()
                .eq(type != null, "type", type)
                .eq(status != null, "status", status)
                .list();
        if (vouchers == null || vouchers.isEmpty()) {
            return Result.ok();
        }
        // 填充秒杀券的库存和时间信息
        List<Long> seckillIds = vouchers.stream()
                .filter(v -> v.getType() != null && v.getType() == 1)
                .map(Voucher::getId)
                .collect(Collectors.toList());
        if (!seckillIds.isEmpty()) {
            List<SeckillVoucher> seckillVouchers = seckillVoucherService.listByIds(seckillIds);
            for (Voucher voucher : vouchers) {
                if (voucher.getType() != null && voucher.getType() == 1) {
                    seckillVouchers.stream()
                            .filter(sv -> sv.getVoucherId().equals(voucher.getId()))
                            .findFirst()
                            .ifPresent(sv -> {
                                voucher.setStock(sv.getStock());
                                voucher.setBeginTime(sv.getBeginTime());
                                voucher.setEndTime(sv.getEndTime());
                            });
                }
            }
        }
        return Result.ok(vouchers);
    }

    @Override
    public Result queryVoucherById(Long id) {
        Voucher voucher = getById(id);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        // 如果是秒杀券，关联查询秒杀信息
        if (voucher.getType() != null && voucher.getType() == 1) {
            SeckillVoucher seckillVoucher = seckillVoucherService.getById(id);
            if (seckillVoucher != null) {
                voucher.setStock(seckillVoucher.getStock());
                voucher.setBeginTime(seckillVoucher.getBeginTime());
                voucher.setEndTime(seckillVoucher.getEndTime());
            }
        }
        return Result.ok(voucher);
    }
}
