package com.market.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class VoucherOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long voucherId;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;

    /** 优惠券标题 */
    private String voucherTitle;

    /** 优惠券副标题 */
    private String voucherSubTitle;

    /** 支付金额（分） */
    private Long payValue;

    /** 抵扣金额（分） */
    private Long actualValue;

    /** 优惠券类型 0-普通 1-秒杀 */
    private Integer voucherType;

    /** 使用规则 */
    private String voucherRules;
}
