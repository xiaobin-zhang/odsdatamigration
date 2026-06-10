package com.example.validator.datasource;

import com.example.validator.csv.SqlSafetyValidator;
import com.example.validator.domain.QueryResult;
import com.example.validator.safety.SafetyViolationException;
import com.example.validator.safety.SqlGuardDecision;
import com.example.validator.safety.SqlGuardService;
import com.example.validator.safety.TooManyResultRowsException;
import com.example.validator.config.ValidatorProperties;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 动态 SQL 查询执行器。
 *
 * <p>职责：按任务指定的数据源名称执行核验 SQL，并返回查询结果与耗时。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@Component
public class QueryExecutor {
    private final NamedDataSourceManager dataSourceManager;
    private final SqlSafetyValidator sqlSafetyValidator;
    private final SqlGuardService sqlGuardService;
    private final ValidatorProperties properties;
    private final Semaphore heavyQuerySemaphore;

    /**
     * 创建查询执行器。
     *
     * @param dataSourceManager 命名数据源管理器
     * @param sqlSafetyValidator SQL 安全校验器
     */
    public QueryExecutor(NamedDataSourceManager dataSourceManager, SqlSafetyValidator sqlSafetyValidator,
                         SqlGuardService sqlGuardService, ValidatorProperties properties) {
        this.dataSourceManager = dataSourceManager;
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.sqlGuardService = sqlGuardService;
        this.properties = properties;
        int permits = Math.max(1, properties.getSafety().getMaxConcurrentHeavyQueries());
        this.heavyQuerySemaphore = new Semaphore(permits);
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
        SqlGuardDecision decision = sqlGuardService.inspect(dataSourceName, dataSource, sql);
        if (decision.isBlocked()) {
            throw new SafetyViolationException(decision);
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(timeoutSeconds);
        long started = System.currentTimeMillis();
        boolean acquired = false;
        try {
            if (decision.isHeavyQuery()) {
                heavyQuerySemaphore.acquire();
                acquired = true;
            }
            List<Map<String, Object>> rows = queryWithRowLimit(jdbcTemplate, sql);
            return new QueryResult(rows, System.currentTimeMillis() - started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for heavy SQL permit", e);
        } finally {
            if (acquired) {
                heavyQuerySemaphore.release();
            }
        }
    }

    private List<Map<String, Object>> queryWithRowLimit(JdbcTemplate jdbcTemplate, String sql) {
        final int maxRows = Math.max(1, properties.getSafety().getMaxResultRows());
        return jdbcTemplate.query(sql, rs -> {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                if (rows.size() >= maxRows) {
                    throw new TooManyResultRowsException(maxRows);
                }
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= columnCount; i++) {
                    String label = metaData.getColumnLabel(i);
                    row.put(label, rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        });
    }
}
