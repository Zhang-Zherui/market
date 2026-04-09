package com.market.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Properties;

@Configuration
public class SentinelNacosDataSourceConfig {

    @Resource
    private SentinelProperties sentinelProperties;

    @PostConstruct
    public void init() {
        if (!sentinelProperties.isEnabled()) {
            return;
        }

        SentinelProperties.Nacos nacos = sentinelProperties.getNacos();
        Properties properties = new Properties();
        properties.setProperty("serverAddr", nacos.getServerAddr());
        if (nacos.getNamespace() != null && !nacos.getNamespace().trim().isEmpty()) {
            properties.setProperty("namespace", nacos.getNamespace());
        }
        properties.setProperty("username", nacos.getUsername());
        properties.setProperty("password", nacos.getPassword());

        ReadableDataSource<String, List<FlowRule>> flowRuleDataSource = new NacosDataSource<>(
                properties,
                nacos.getGroupId(),
                nacos.getFlowDataId(),
                source -> source == null || source.trim().isEmpty()
                        ? java.util.Collections.emptyList()
                        : JSON.parseArray(source, FlowRule.class)
        );

        FlowRuleManager.register2Property(flowRuleDataSource.getProperty());
    }
}
