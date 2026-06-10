package com.example.validator.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 核验程序配置属性。
 *
 * <p>职责：承载 application.yml 中 validator 前缀下的数据源、配对关系、并发和续跑配置。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@ConfigurationProperties(prefix = "validator")
public class ValidatorProperties {
    private String csvPath = "classpath:validation_tables.csv";
    private boolean resume = true;
    private boolean rerunPassed = false;
    private Execution execution = new Execution();
    private Safety safety = new Safety();
    private Map<String, DbConfig> datasources = new LinkedHashMap<String, DbConfig>();
    private List<ComparePair> comparePairs = new ArrayList<ComparePair>();

    public String getCsvPath() { return csvPath; }
    public void setCsvPath(String csvPath) { this.csvPath = csvPath; }
    public boolean isResume() { return resume; }
    public void setResume(boolean resume) { this.resume = resume; }
    public boolean isRerunPassed() { return rerunPassed; }
    public void setRerunPassed(boolean rerunPassed) { this.rerunPassed = rerunPassed; }
    public Execution getExecution() { return execution; }
    public void setExecution(Execution execution) { this.execution = execution; }
    public Safety getSafety() { return safety; }
    public void setSafety(Safety safety) { this.safety = safety; }
    public Map<String, DbConfig> getDatasources() { return datasources; }
    public void setDatasources(Map<String, DbConfig> datasources) { this.datasources = datasources; }
    public List<ComparePair> getComparePairs() { return comparePairs; }
    public void setComparePairs(List<ComparePair> comparePairs) { this.comparePairs = comparePairs; }

    /**
     * 执行并发与超时配置。
     *
     * <p>职责：控制多库、多表、分片和校验项的并行度，以及单条 SQL 超时时间。</p>
     *
     * @author zxb
     * @since 2026-06-03
     */
    public static class Execution {
        private int pairParallelism = 1;
        private int tableParallelism = 4;
        private int shardParallelism = 2;
        private int checkParallelism = 1;
        private int queryTimeoutSeconds = 1800;
        private int retryTimes = 1;

        public int getPairParallelism() { return pairParallelism; }
        public void setPairParallelism(int pairParallelism) { this.pairParallelism = pairParallelism; }
        public int getTableParallelism() { return tableParallelism; }
        public void setTableParallelism(int tableParallelism) { this.tableParallelism = tableParallelism; }
        public int getShardParallelism() { return shardParallelism; }
        public void setShardParallelism(int shardParallelism) { this.shardParallelism = shardParallelism; }
        public int getCheckParallelism() { return checkParallelism; }
        public void setCheckParallelism(int checkParallelism) { this.checkParallelism = checkParallelism; }
        public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
        public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
        public int getRetryTimes() { return retryTimes; }
        public void setRetryTimes(int retryTimes) { this.retryTimes = retryTimes; }
    }

    public static class Safety {
        private boolean enabled = true;
        private long largeTableThresholdGb = 100;
        private int maxConcurrentHeavyQueries = 1;
        private int maxResultRows = 10000;
        private boolean requireShardForLargeTable = true;
        private boolean forbidOffsetShardForLargeTable = true;
        private boolean forbidUnindexedOrderByForLargeTable = true;
        private boolean explainBeforeExecute = true;
        private long explainMaxRows = 50000000L;
        private int explainMaxFullScanTables = 0;
        private OnViolation onViolation = OnViolation.SKIP;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getLargeTableThresholdGb() { return largeTableThresholdGb; }
        public void setLargeTableThresholdGb(long largeTableThresholdGb) { this.largeTableThresholdGb = largeTableThresholdGb; }
        public int getMaxConcurrentHeavyQueries() { return maxConcurrentHeavyQueries; }
        public void setMaxConcurrentHeavyQueries(int maxConcurrentHeavyQueries) { this.maxConcurrentHeavyQueries = maxConcurrentHeavyQueries; }
        public int getMaxResultRows() { return maxResultRows; }
        public void setMaxResultRows(int maxResultRows) { this.maxResultRows = maxResultRows; }
        public boolean isRequireShardForLargeTable() { return requireShardForLargeTable; }
        public void setRequireShardForLargeTable(boolean requireShardForLargeTable) { this.requireShardForLargeTable = requireShardForLargeTable; }
        public boolean isForbidOffsetShardForLargeTable() { return forbidOffsetShardForLargeTable; }
        public void setForbidOffsetShardForLargeTable(boolean forbidOffsetShardForLargeTable) { this.forbidOffsetShardForLargeTable = forbidOffsetShardForLargeTable; }
        public boolean isForbidUnindexedOrderByForLargeTable() { return forbidUnindexedOrderByForLargeTable; }
        public void setForbidUnindexedOrderByForLargeTable(boolean forbidUnindexedOrderByForLargeTable) { this.forbidUnindexedOrderByForLargeTable = forbidUnindexedOrderByForLargeTable; }
        public boolean isExplainBeforeExecute() { return explainBeforeExecute; }
        public void setExplainBeforeExecute(boolean explainBeforeExecute) { this.explainBeforeExecute = explainBeforeExecute; }
        public long getExplainMaxRows() { return explainMaxRows; }
        public void setExplainMaxRows(long explainMaxRows) { this.explainMaxRows = explainMaxRows; }
        public int getExplainMaxFullScanTables() { return explainMaxFullScanTables; }
        public void setExplainMaxFullScanTables(int explainMaxFullScanTables) { this.explainMaxFullScanTables = explainMaxFullScanTables; }
        public OnViolation getOnViolation() { return onViolation; }
        public void setOnViolation(OnViolation onViolation) { this.onViolation = onViolation; }
    }

    public enum OnViolation {
        SKIP,
        ERROR
    }

    /**
     * 单个业务数据源配置。
     *
     * <p>职责：描述 TDSQL、OceanBase 或测试 H2 实例的 JDBC 连接信息。</p>
     *
     * @author zxb
     * @since 2026-06-03
     */
    public static class DbConfig {
        private String jdbcUrl;
        private String username;
        private String password;
        private int maxPoolSize = 5;
        private SqlDialect dialect = SqlDialect.AUTO;

        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public SqlDialect getDialect() { return dialect; }
        public void setDialect(SqlDialect dialect) { this.dialect = dialect; }
    }

    public SqlDialect resolveDialect(String datasourceName) {
        DbConfig config = datasources.get(datasourceName);
        if (config == null) {
            throw new IllegalArgumentException("未找到数据源: " + datasourceName);
        }
        if (config.getDialect() != null && config.getDialect() != SqlDialect.AUTO) {
            return config.getDialect();
        }
        String jdbcUrl = config.getJdbcUrl() == null ? "" : config.getJdbcUrl().toLowerCase();
        if (jdbcUrl.contains(":h2:")) {
            return SqlDialect.H2;
        }
        if (jdbcUrl.contains(":oracle:")) {
            return SqlDialect.ORACLE;
        }
        return SqlDialect.MYSQL;
    }

    public enum SqlDialect {
        AUTO,
        H2,
        MYSQL,
        TDSQL_MYSQL,
        OCEANBASE_MYSQL,
        OCEANBASE_ORACLE,
        ORACLE
    }

    /**
     * 数据库核验配对配置。
     *
     * <p>职责：声明一个源库与一个目标库之间的两两核验关系。</p>
     *
     * @author zxb
     * @since 2026-06-03
     */
    public static class ComparePair {
        private String name;
        private String source;
        private String target;
        private boolean enabled = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
