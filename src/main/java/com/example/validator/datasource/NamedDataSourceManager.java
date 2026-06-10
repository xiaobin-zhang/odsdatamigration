package com.example.validator.datasource;

import com.example.validator.config.ValidatorProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * 命名数据源管理器。
 *
 * <p>职责：根据配置创建多个 HikariCP 数据源，并按名称提供给核验任务使用。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@Component
public class NamedDataSourceManager {
    private final Map<String, DataSource> dataSourceMap = new LinkedHashMap<String, DataSource>();

    /**
     * 根据程序配置初始化所有业务数据源。
     *
     * @param properties 核验程序配置
     */
    public NamedDataSourceManager(ValidatorProperties properties) {
        for (Map.Entry<String, ValidatorProperties.DbConfig> entry : properties.getDatasources().entrySet()) {
            ValidatorProperties.DbConfig config = entry.getValue();
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(config.getJdbcUrl());
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
            hikariConfig.setPoolName("validator-" + entry.getKey());
            dataSourceMap.put(entry.getKey(), new HikariDataSource(hikariConfig));
        }
    }

    /**
     * 获取指定名称的数据源。
     *
     * @param name 数据源名称
     * @return 数据源实例
     */
    public DataSource getRequired(String name) {
        DataSource dataSource = dataSourceMap.get(name);
        if (dataSource == null) {
            throw new IllegalArgumentException("未找到数据源: " + name);
        }
        return dataSource;
    }
}
