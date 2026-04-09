package com.market.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sentinel")
public class SentinelProperties {

    private boolean enabled = true;

    private String dashboardServer = "localhost:8858";

    private int apiPort = 8719;

    /**
     * 供 dashboard 回连本机时使用，docker 中可设为 host.docker.internal
     */
    private String clientIp = "127.0.0.1";

    private Nacos nacos = new Nacos();

    @Data
    public static class Nacos {
        private String serverAddr = "localhost:8848";
        private String namespace = "";
        private String groupId = "SENTINEL_GROUP";
        private String flowDataId = "market-sentinel-flow-rules";
        private String username = "nacos";
        private String password = "nacos";
    }
}
