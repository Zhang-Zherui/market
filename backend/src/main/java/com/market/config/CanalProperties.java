package com.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Canal 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "canal")
public class CanalProperties {

    /**
     * Canal Server 地址
     */
    private String host = "127.0.0.1";

    /**
     * Canal Server 端口
     */
    private Integer port = 11111;

    /**
     * 目标实例名称（在 Canal Server 中配置的）
     */
    private String destination = "example";

    /**
     * 用户名（无认证时为空）
     */
    private String username = "";

    /**
     * 密码（无认证时为空）
     */
    private String password = "";

    /**
     * 需要监听的数据库
     */
    private String database = "market";

    /**
     * 批量获取的数量
     */
    private Integer batchSize = 1000;
}
