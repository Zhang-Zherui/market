package com.market.config;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.*;

import java.util.List;

import com.alibaba.otter.canal.protocol.Message;
import com.market.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Canal 客户端
 * 监听 MySQL binlog 变更，自动同步/删除 Redis 缓存
 */
@Slf4j
@Component
public class CanalClient {

    @Autowired
    private CanalProperties canalProperties;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private CanalConnector connector;

    private volatile boolean running = false;

    /**
     * 初始化 Canal 连接并启动监听线程
     */
    @PostConstruct
    public void init() {
        // 创建 Canal 连接
        connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(canalProperties.getHost(), canalProperties.getPort()),
                canalProperties.getDestination(),
                canalProperties.getUsername(),
                canalProperties.getPassword()
        );

        // 启动监听线程
        running = true;
        Thread thread = new Thread(this::startListening, "canal-listener");
        thread.setDaemon(true);
        thread.start();

        log.info("Canal 客户端启动成功，开始监听 binlog...");
    }

    /**
     * 监听 binlog 变更
     */
    private void startListening() {
        int retryCount = 0;
        int maxRetry = 10;

        while (running && retryCount < maxRetry) {
            try {
                // 连接 Canal Server
                connector.connect();
                // 订阅指定数据库的所有表
                connector.subscribe(canalProperties.getDatabase() + "\\..*");
                // 回滚到未进行 ack 的位置
                connector.rollback();

                log.info("Canal 连接成功，开始订阅数据库: {}", canalProperties.getDatabase());

                while (running) {
                    // 批量获取数据
                    Message message = connector.getWithoutAck(canalProperties.getBatchSize());
                    long batchId = message.getId();

                    if (batchId == -1 || message.getEntries().isEmpty()) {
                        // 没有数据，休眠1秒
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }

                    // 处理变更数据
                    processEntries(message.getEntries());

                    // 确认消息
                    connector.ack(batchId);
                }

            } catch (Exception e) {
                retryCount++;
                log.error("Canal 连接异常，正在重试 ({}/{}): {}", retryCount, maxRetry, e.getMessage());

                try {
                    Thread.sleep(5000); // 5秒后重试
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (!running) {
            log.info("Canal 客户端已停止");
        } else {
            log.error("Canal 连接失败，已达到最大重试次数: {}", maxRetry);
        }
    }

    /**
     * 处理 binlog 变更条目
     */
    private void processEntries(List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.getEntryType() == EntryType.TRANSACTIONBEGIN ||
                    entry.getEntryType() == EntryType.TRANSACTIONEND) {
                continue;
            }

            RowChange rowChange;
            try {
                rowChange = RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                log.error("解析 binlog 条目失败: {}", e.getMessage());
                continue;
            }

            EventType eventType = rowChange.getEventType();
            String tableName = entry.getHeader().getTableName();
            String schemaName = entry.getHeader().getSchemaName();

            log.debug("监听到变更: database={}, table={}, eventType={}", schemaName, tableName, eventType);

            // 处理优惠券表的变更
            if ("tb_voucher".equalsIgnoreCase(tableName)) {
                handleVoucherChange(eventType, rowChange.getRowDatasList());
            }
        }
    }

    /**
     * 处理优惠券表变更
     */
    private void handleVoucherChange(EventType eventType, List<RowData> rowDataList) {
        for (RowData rowData : rowDataList) {
            Long voucherId = getVoucherId(rowData);

            if (voucherId == null) {
                log.warn("无法获取优惠券ID，跳过处理");
                continue;
            }

            switch (eventType) {
                case UPDATE:
                    handleVoucherUpdate(voucherId, rowData);
                    break;
                case DELETE:
                    handleVoucherDelete(voucherId);
                    break;
                case INSERT:
                    handleVoucherInsert(voucherId);
                    break;
                default:
                    log.debug("忽略事件类型: {}", eventType);
            }
        }
    }

    /**
     * 获取优惠券 ID
     */
    private Long getVoucherId(RowData rowData) {
        try {
            // 优先从变更后的数据获取
            if (rowData.getAfterColumnsCount() > 0) {
                for (Column column : rowData.getAfterColumnsList()) {
                    if ("id".equalsIgnoreCase(column.getName())) {
                        return Long.parseLong(column.getValue());
                    }
                }
            }
            // 删除时从变更前的数据获取
            if (rowData.getBeforeColumnsCount() > 0) {
                for (Column column : rowData.getBeforeColumnsList()) {
                    if ("id".equalsIgnoreCase(column.getName())) {
                        return Long.parseLong(column.getValue());
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析优惠券ID失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 处理优惠券更新：删除缓存，下次查询时重新加载
     */
    private void handleVoucherUpdate(Long voucherId, RowData rowData) {
        String cacheKey = RedisConstants.CACHE_VOUCHER_KEY + voucherId;
        stringRedisTemplate.delete(cacheKey);
        log.info("优惠券更新，已删除缓存: voucherId={}, cacheKey={}", voucherId, cacheKey);

        // 检查是否修改了库存，同步更新 Redis 秒杀库存
        if (hasStockChanged(rowData)) {
            String newStock = getColumnValue(rowData.getAfterColumnsList(), "stock");
            if (newStock != null) {
                String seckillKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
                stringRedisTemplate.opsForValue().set(seckillKey, newStock);
                log.info("库存变更，已更新秒杀库存缓存: voucherId={}, stock={}", voucherId, newStock);
            }
        }
    }

    /**
     * 处理优惠券删除：删除缓存
     */
    private void handleVoucherDelete(Long voucherId) {
        String cacheKey = RedisConstants.CACHE_VOUCHER_KEY + voucherId;
        stringRedisTemplate.delete(cacheKey);

        String seckillKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        stringRedisTemplate.delete(seckillKey);

        log.info("优惠券删除，已清理缓存: voucherId={}", voucherId);
    }

    /**
     * 处理优惠券新增：不做处理，查询时自动加载
     */
    private void handleVoucherInsert(Long voucherId) {
        // 新增的优惠券在查询时自动缓存，无需处理
        log.info("监听到新增优惠券: voucherId={}", voucherId);
    }

    /**
     * 检查库存是否变更
     */
    private boolean hasStockChanged(RowData rowData) {
        for (Column column : rowData.getAfterColumnsList()) {
            if ("stock".equalsIgnoreCase(column.getName()) && column.getUpdated()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取指定列的值
     */
    private String getColumnValue(List<Column> columns, String columnName) {
        for (Column column : columns) {
            if (columnName.equalsIgnoreCase(column.getName())) {
                return column.getValue();
            }
        }
        return null;
    }

    /**
     * 销毁时关闭连接
     */
    @PreDestroy
    public void destroy() {
        running = false;
        if (connector != null) {
            try {
                connector.disconnect();
                log.info("Canal 连接已关闭");
            } catch (Exception e) {
                log.error("关闭 Canal 连接失败: {}", e.getMessage());
            }
        }
    }
}
