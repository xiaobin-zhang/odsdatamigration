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
 * @author Codex
 * @since 2026-06-03
 */
@ConfigurationProperties(prefix = "validator")
public class ValidatorProperties {
    private String csvPath = "classpath:validation_tables.csv";
    private boolean resume = true;
    private boolean rerunPassed = false;
    private Execution execution = new Execution();
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
    public Map<String, DbConfig> getDatasources() { return datasources; }
    public void setDatasources(Map<String, DbConfig> datasources) { this.datasources = datasources; }
    public List<ComparePair> getComparePairs() { return comparePairs; }
    public void setComparePairs(List<ComparePair> comparePairs) { this.comparePairs = comparePairs; }

    /**
     * 执行并发与超时配置。
     *
     * <p>职责：控制多库、多表、分片和校验项的并行度，以及单条 SQL 超时时间。</p>
     *
     * @author Codex
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

    /**
     * 单个业务数据源配置。
     *
     * <p>职责：描述 TDSQL、OceanBase 或测试 H2 实例的 JDBC 连接信息。</p>
     *
     * @author Codex
     * @since 2026-06-03
     */
    public static class DbConfig {
        private String jdbcUrl;
        private String username;
        private String password;
        private int maxPoolSize = 5;

        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    }

    /**
     * 数据库核验配对配置。
     *
     * <p>职责：声明一个源库与一个目标库之间的两两核验关系。</p>
     *
     * @author Codex
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
