package com.market.ratelimit;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.market.config.SentinelProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SentinelRateLimitExecutor implements RateLimitExecutor {

    @Resource
    private SentinelProperties sentinelProperties;

    private final ConcurrentMap<String, Boolean> initializedRules = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "sentinel";
    }

    @Override
    public RateLimitHandle acquire(String resource, double permitsPerSecond, long timeoutMs) {
        ensureRule(resource, permitsPerSecond);
        try {
            Entry entry = SphU.entry(resource);
            return new RateLimitHandle() {
                @Override
                public boolean isAcquired() {
                    return true;
                }

                @Override
                public void exit() {
                    entry.exit();
                }
            };
        } catch (BlockException e) {
            return new RateLimitHandle() {
                @Override
                public boolean isAcquired() {
                    return false;
                }

                @Override
                public void exit() {
                    // no-op
                }
            };
        }
    }

    private void ensureRule(String resource, double permitsPerSecond) {
        if (initializedRules.putIfAbsent(resource, Boolean.TRUE) != null) {
            return;
        }

        FlowRule targetRule = buildRule(resource, permitsPerSecond);
        List<FlowRule> currentRules = new ArrayList<>(FlowRuleManager.getRules());
        boolean exists = currentRules.stream().anyMatch(rule -> resource.equals(rule.getResource()));
        if (!exists) {
            currentRules.add(targetRule);
            FlowRuleManager.loadRules(currentRules);
            publishRulesToNacos(currentRules);
        }
    }

    private FlowRule buildRule(String resource, double permitsPerSecond) {
        FlowRule flowRule = new FlowRule();
        flowRule.setResource(resource);
        flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        flowRule.setCount(permitsPerSecond);
        flowRule.setLimitApp("default");
        flowRule.setStrategy(RuleConstant.STRATEGY_DIRECT);
        flowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return flowRule;
    }

    private void publishRulesToNacos(List<FlowRule> rules) {
        try {
            SentinelProperties.Nacos nacos = sentinelProperties.getNacos();
            Properties properties = new Properties();
            properties.setProperty("serverAddr", nacos.getServerAddr());
            if (nacos.getNamespace() != null && !nacos.getNamespace().trim().isEmpty()) {
                properties.setProperty("namespace", nacos.getNamespace());
            }
            properties.setProperty("username", nacos.getUsername());
            properties.setProperty("password", nacos.getPassword());

            ConfigService configService = NacosFactory.createConfigService(properties);
            String groupId = nacos.getGroupId();
            String dataId = nacos.getFlowDataId();

            String existingConfig = configService.getConfig(dataId, groupId, 3000);
            List<FlowRule> mergedRules = existingConfig == null || existingConfig.trim().isEmpty()
                    ? new ArrayList<>()
                    : JSON.parseObject(existingConfig, new TypeReference<List<FlowRule>>() {});

            for (FlowRule rule : rules) {
                boolean exists = mergedRules.stream().anyMatch(item -> item.getResource().equals(rule.getResource()));
                if (!exists) {
                    mergedRules.add(rule);
                }
            }

            configService.publishConfig(dataId, groupId, JSON.toJSONString(mergedRules));
        } catch (Exception ignored) {
            // Nacos 不可用时仍允许使用当前内存规则
        }
    }
}
