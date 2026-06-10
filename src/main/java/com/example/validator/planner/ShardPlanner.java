package com.example.validator.planner;

import com.example.validator.config.ValidatorProperties;
import com.example.validator.datasource.NamedDataSourceManager;
import com.example.validator.datasource.QueryExecutor;
import com.example.validator.domain.QueryResult;
import com.example.validator.domain.ShardRange;
import com.example.validator.domain.TableRule;
import com.example.validator.safety.SqlGuardService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ShardPlanner {
    private final QueryExecutor queryExecutor;
    private final ValidatorProperties properties;
    private final SqlGuardService sqlGuardService;
    private final NamedDataSourceManager dataSourceManager;

    public ShardPlanner(QueryExecutor queryExecutor, ValidatorProperties properties,
                        SqlGuardService sqlGuardService, NamedDataSourceManager dataSourceManager) {
        this.queryExecutor = queryExecutor;
        this.properties = properties;
        this.sqlGuardService = sqlGuardService;
        this.dataSourceManager = dataSourceManager;
    }

    public TableRule expand(ValidatorProperties.ComparePair pair, TableRule rule) {
        if (!requiresOffsetExpansion(rule)) {
            return rule;
        }
        if (properties.getSafety().isForbidOffsetShardForLargeTable()
                && sqlGuardService.isLargeTable(pair.getSource(), dataSourceManager.getRequired(pair.getSource()), rule.getSourceTable())) {
            throw new IllegalArgumentException("Large table cannot use OFFSET shard because it requires count/order/offset. table="
                    + rule.getSourceTable() + ", use RANGE or INTERVAL shard instead");
        }
        int shardCount = rule.getShardRanges().get(0).getShardCount();
        long total = sourceCount(pair, rule);
        int limit = (int) Math.max(1, (total + shardCount - 1) / shardCount);
        List<ShardRange> ranges = new ArrayList<ShardRange>();
        for (int i = 0; i < shardCount; i++) {
            ranges.add(ShardRange.offset(i * limit, limit));
        }
        TableRule expanded = copy(rule);
        expanded.setShardRanges(ranges);
        return expanded;
    }

    private boolean requiresOffsetExpansion(TableRule rule) {
        return rule.getShardRanges().size() == 1
                && rule.getShardRanges().get(0).getStrategy() == ShardRange.Strategy.OFFSET
                && rule.getShardRanges().get(0).getShardCount() > 0;
    }

    private long sourceCount(ValidatorProperties.ComparePair pair, TableRule rule) {
        String where = StringUtils.hasText(rule.getWhereClause()) ? rule.getWhereClause() : "1=1";
        String sql = "select count(*) as total_count from " + rule.getSourceTable() + " where " + where;
        QueryResult result = queryExecutor.query(pair.getSource(), sql, properties.getExecution().getQueryTimeoutSeconds());
        Map<String, Object> row = result.getRows().get(0);
        Object value = row.values().iterator().next();
        return Long.parseLong(String.valueOf(value));
    }

    private TableRule copy(TableRule source) {
        TableRule target = new TableRule();
        target.setPairName(source.getPairName());
        target.setEnabled(source.isEnabled());
        target.setSourceTable(source.getSourceTable());
        target.setTargetTable(source.getTargetTable());
        target.setPrimaryKey(source.getPrimaryKey());
        target.setPrimaryKeys(new ArrayList<String>(source.getPrimaryKeys()));
        target.setCheckers(new ArrayList<com.example.validator.common.CheckType>(source.getCheckers()));
        target.setWhereClause(source.getWhereClause());
        target.setAmountFields(new ArrayList<String>(source.getAmountFields()));
        target.setDateField(source.getDateField());
        target.setNullFields(new ArrayList<String>(source.getNullFields()));
        target.setOrderFields(new ArrayList<String>(source.getOrderFields()));
        target.setCompareFields(new ArrayList<String>(source.getCompareFields()));
        target.setSampleWhere(source.getSampleWhere());
        target.setSampleLimit(source.getSampleLimit());
        target.setShardColumn(source.getShardColumn());
        target.setShardType(source.getShardType());
        target.setShardRanges(new ArrayList<ShardRange>(source.getShardRanges()));
        target.setAmountTolerance(source.getAmountTolerance());
        return target;
    }
}
