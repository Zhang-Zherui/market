package com.market.job;

import com.market.service.IVoucherOrderService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class VoucherOrderAutoRefundJob {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @XxlJob("expirePaidVoucherOrdersJob")
    public void expirePaidVoucherOrders() {
        int affected = voucherOrderService.expirePaidOrders();
        XxlJobHelper.log("scan finished, expired paid voucher orders: {}", affected);
    }
}
