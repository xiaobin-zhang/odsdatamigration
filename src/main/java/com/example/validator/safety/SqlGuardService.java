package com.example.validator.safety;

import com.example.validator.config.ValidatorProperties;
import com.example.validator.config.ValidatorProperties.SqlDialect;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SqlGuardService {
    private static final Pattern FROM_TABLE = Pattern.compile("\\bfrom\\s+([a-zA-Z0-9_.$]+)", Pattern.CASE_INSENSITIVE);

    private final ValidatorProperties properties;
    private final Map<String, Boolean> largeTableCache = new ConcurrentHashMap<String, Boolean>();

    public SqlGuardService(ValidatorProperties properties) {
        this.properties = properties;
    }

    public SqlGuardDecision inspect(String dataSourceName, DataSource dataSource, String sql) {
        ValidatorProperties.Safety safety = properties.getSafety();
        if (safety == null || !safety.isEnabled()) {
            return SqlGuardDecision.allow("SQL safety guard disabled", isHeavySql(sql));
        }
        String normalized = normalize(sql);
        String table = firstTable(normalized);
        boolean largeTable = table != null && isLargeTable(dataSourceName, dataSource, table);
        boolean heavySql = isHeavySql(normalized);

        if (largeTable && safety.isForbidOffsetShardForLargeTable() && containsOffsetShard(normalized)) {
            return SqlGuardDecision.block("Large table SQL uses OFFSET shard; use RANGE or INTERVAL shard instead. table=" + table);
        }
        if (largeTable && safety.isRequireShardForLargeTable() && isAggregateSql(normalized)
                && !hasProtectiveShardPredicate(normalized)) {
            return SqlGuardDecision.block("Large table aggregate SQL has no protective shard predicate. table=" + table);
        }
        if (largeTable && safety.isForbidUnindexedOrderByForLargeTable() && hasOrderBy(normalized)
                && !orderByUsesKnownIndex(dataSource, table, normalized)) {
            return SqlGuardDecision.block("Large table ORDER BY does not match a known primary key or index prefix. table=" + table);
        }

        if (largeTable && safety.isExplainBeforeExecute()) {
            SqlGuardDecision explainDecision = explain(dataSourceName, dataSource, normalized);
            if (explainDecision.isBlocked()) {
                return explainDecision;
            }
            if (explainDecision.getRiskLevel() == QueryRiskLevel.WARN) {
                return explainDecision;
            }
        }
        return SqlGuardDecision.allow(largeTable ? "Large table SQL passed safety checks" : "SQL passed safety checks", heavySql);
    }

    public boolean isLargeTable(String dataSourceName, DataSource dataSource, String tableName) {
        ValidatorProperties.Safety safety = properties.getSafety();
        if (safety == null || !safety.isEnabled()) {
            return false;
        }
        String cacheKey = dataSourceName + "|" + properties.resolveDialect(dataSourceName) + "|" + tableName;
        Boolean cached = largeTableCache.get(cacheKey);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean large = resolveLargeTable(dataSourceName, dataSource, tableName);
        largeTableCache.put(cacheKey, large);
        return large;
    }

    private boolean resolveLargeTable(String dataSourceName, DataSource dataSource, String tableName) {
        SqlDialect dialect = properties.resolveDialect(dataSourceName);
        if (dialect == SqlDialect.H2) {
            return false;
        }
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String cleanTable = unqualifiedTable(tableName);
            Long bytes = jdbcTemplate.queryForObject(
                    "select coalesce(data_length,0) + coalesce(index_length,0) from information_schema.tables "
                            + "where table_schema = database() and table_name = ?",
                    new Object[]{cleanTable}, Long.class);
            if (bytes == null) {
                return true;
            }
            long thresholdBytes = properties.getSafety().getLargeTableThresholdGb() * 1024L * 1024L * 1024L;
            return bytes.longValue() >= thresholdBytes;
        } catch (Exception e) {
            return true;
        }
    }

    private SqlGuardDecision explain(String dataSourceName, DataSource dataSource, String normalizedSql) {
        SqlDialect dialect = properties.resolveDialect(dataSourceName);
        if (dialect == SqlDialect.H2) {
            return SqlGuardDecision.allow("H2 explain skipped", isHeavySql(normalizedSql));
        }
        if (!supportsMysqlExplain(dialect)) {
            return SqlGuardDecision.allow("EXPLAIN skipped for unsupported dialect: " + dialect, isHeavySql(normalizedSql));
        }
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("explain " + normalizedSql);
            long estimatedRows = 0L;
            int fullScanTables = 0;
            boolean unindexedOrder = false;
            for (Map<String, Object> row : rows) {
                String type = lower(value(row, "type"));
                String key = lower(value(row, "key"));
                String extra = lower(value(row, "extra"));
                estimatedRows += parseLong(value(row, "rows"));
                if ("all".equals(type)) {
                    fullScanTables++;
                }
                if (extra.contains("filesort") && (key.length() == 0 || "null".equals(key))) {
                    unindexedOrder = true;
                }
                if (extra.contains("using temporary")) {
                    return SqlGuardDecision.block("EXPLAIN shows temporary table usage: " + extra);
                }
            }
            ValidatorProperties.Safety safety = properties.getSafety();
            if (estimatedRows > safety.getExplainMaxRows()) {
                return SqlGuardDecision.block("EXPLAIN estimated rows exceed limit: rows=" + estimatedRows);
            }
            if (fullScanTables > safety.getExplainMaxFullScanTables()) {
                return SqlGuardDecision.block("EXPLAIN shows full table scan: fullScanTables=" + fullScanTables);
            }
            if (safety.isForbidUnindexedOrderByForLargeTable() && hasOrderBy(normalizedSql) && unindexedOrder) {
                return SqlGuardDecision.block("EXPLAIN shows unindexed ORDER BY/filesort on large table");
            }
            return SqlGuardDecision.allow("EXPLAIN passed", isHeavySql(normalizedSql));
        } catch (Exception e) {
            return SqlGuardDecision.block("EXPLAIN failed; SQL is treated as unsafe: " + e.getMessage());
        }
    }

    private boolean supportsMysqlExplain(SqlDialect dialect) {
        return dialect == SqlDialect.MYSQL
                || dialect == SqlDialect.TDSQL_MYSQL
                || dialect == SqlDialect.OCEANBASE_MYSQL;
    }

    private boolean isHeavySql(String sql) {
        String normalized = normalize(sql);
        return isAggregateSql(normalized) || hasOrderBy(normalized);
    }

    private boolean isAggregateSql(String normalized) {
        return normalized.contains("count(")
                || normalized.contains("sum(")
                || normalized.contains(" group by ")
                || normalized.contains("case when ");
    }

    private boolean hasOrderBy(String normalized) {
        return normalized.contains(" order by ");
    }

    private boolean orderByUsesKnownIndex(DataSource dataSource, String table, String normalizedSql) {
        List<String> orderColumns = orderColumns(normalizedSql);
        if (orderColumns.isEmpty()) {
            return true;
        }
        String cleanTable = unqualifiedTable(table);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            if (matchesPrimaryKey(metaData, cleanTable, orderColumns)) {
                return true;
            }
            return matchesIndex(metaData, cleanTable, orderColumns);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesPrimaryKey(DatabaseMetaData metaData, String table, List<String> orderColumns) throws Exception {
        List<String> primaryKeys = new ArrayList<String>();
        try (ResultSet rs = metaData.getPrimaryKeys(null, null, table)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (primaryKeys.isEmpty()) {
            try (ResultSet rs = metaData.getPrimaryKeys(null, null, table.toUpperCase(Locale.ROOT))) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return startsWithColumns(primaryKeys, orderColumns);
    }

    private boolean matchesIndex(DatabaseMetaData metaData, String table, List<String> orderColumns) throws Exception {
        Set<String> candidates = new HashSet<String>();
        candidates.add(table);
        candidates.add(table.toUpperCase(Locale.ROOT));
        candidates.add(table.toLowerCase(Locale.ROOT));
        for (String candidate : candidates) {
            Map<String, List<String>> indexes = new java.util.LinkedHashMap<String, List<String>>();
            try (ResultSet rs = metaData.getIndexInfo(null, null, candidate, false, false)) {
                while (rs.next()) {
                    short position = rs.getShort("ORDINAL_POSITION");
                    String column = rs.getString("COLUMN_NAME");
                    String indexName = rs.getString("INDEX_NAME");
                    if (position <= 0 || column == null || indexName == null) {
                        continue;
                    }
                    List<String> columns = indexes.get(indexName);
                    if (columns == null) {
                        columns = new ArrayList<String>();
                        indexes.put(indexName, columns);
                    }
                    while (columns.size() < position) {
                        columns.add(null);
                    }
                    columns.set(position - 1, column.toLowerCase(Locale.ROOT));
                }
            }
            for (List<String> columns : indexes.values()) {
                if (startsWithColumns(columns, orderColumns)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean startsWithColumns(List<String> indexedColumns, List<String> orderColumns) {
        if (indexedColumns.size() < orderColumns.size()) {
            return false;
        }
        for (int i = 0; i < orderColumns.size(); i++) {
            String indexed = indexedColumns.get(i);
            if (indexed == null || !indexed.equals(orderColumns.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<String> orderColumns(String normalizedSql) {
        List<String> columns = new ArrayList<String>();
        int start = normalizedSql.indexOf(" order by ");
        if (start < 0) {
            return columns;
        }
        String clause = normalizedSql.substring(start + " order by ".length());
        int limit = clause.indexOf(" limit ");
        if (limit >= 0) {
            clause = clause.substring(0, limit);
        }
        for (String part : clause.split(",")) {
            String column = part.trim().split("\\s+")[0];
            if (column.length() > 0) {
                int dot = column.lastIndexOf('.');
                columns.add((dot >= 0 ? column.substring(dot + 1) : column).toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private boolean containsOffsetShard(String normalized) {
        return normalized.contains(" offset ") || normalized.contains(" limit ") && normalized.contains(" shard_window");
    }

    private boolean hasProtectiveShardPredicate(String normalized) {
        if (!normalized.contains(" where ")) {
            return false;
        }
        return normalized.contains(" mod(")
                || normalized.contains(" between ")
                || normalized.contains(" >= ") && normalized.contains(" < ")
                || normalized.contains(" in (select ");
    }

    private String normalize(String sql) {
        return sql == null ? "" : sql.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String firstTable(String normalizedSql) {
        Matcher matcher = FROM_TABLE.matcher(normalizedSql);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String unqualifiedTable(String tableName) {
        int index = tableName.lastIndexOf('.');
        return index >= 0 ? tableName.substring(index + 1) : tableName;
    }

    private String value(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            value = row.get(column.toUpperCase(Locale.ROOT));
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private long parseLong(String value) {
        try {
            return value == null || value.length() == 0 ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }
}
