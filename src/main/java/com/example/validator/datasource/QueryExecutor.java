package com.example.validator.datasource;

import com.example.validator.csv.SqlSafetyValidator;
import com.example.validator.domain.QueryResult;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 动态 SQL 查询执行器。
 *
 * <p>职责：按任务指定的数据源名称执行核验 SQL，并返回查询结果与耗时。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Component
public class QueryExecutor {
    private final NamedDataSourceManager dataSourceManager;
    private final SqlSafetyValidator sqlSafetyValidator;

    /**
     * 创建查询执行器。
     *
     * @param dataSourceManager 命名数据源管理器
     * @param sqlSafetyValidator SQL 安全校验器
     */
    public QueryExecutor(NamedDataSourceManager dataSourceManager, SqlSafetyValidator sqlSafetyValidator) {
        this.dataSourceManager = dataSourceManager;
        this.sqlSafetyValidator = sqlSafetyValidator;
    }

    /**
     * 执行单条 SELECT 查询。
     *
     * @param dataSourceName 数据源名称
     * @param sql 待执行 SQL
     * @param timeoutSeconds SQL 超时时间，单位秒
     * @return 查询结果和耗时
     */
    public QueryResult query(String dataSourceName, String sql, int timeoutSeconds) {
        sqlSafetyValidator.assertSelectOnly(sql);
        DataSource dataSource = dataSourceManager.getRequired(dataSourceName);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(timeoutSeconds);
        long started = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return new QueryResult(rows, System.currentTimeMillis() - started);
    }
}
