package com.example.validator.checker;

import com.example.validator.common.ShardType;
import com.example.validator.domain.ShardRange;
import com.example.validator.domain.TableRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class SqlBuilder {
    private SqlBuilder() {
    }

    public static String whereWithShard(String baseWhere, TableRule rule, ShardRange shardRange) {
        return whereWithShard(baseWhere, rule, shardRange, null);
    }

    public static String whereWithShard(String baseWhere, TableRule rule, ShardRange shardRange, String table) {
        String where = StringUtils.hasText(baseWhere) ? baseWhere : "1=1";
        if (shardRange != null && StringUtils.hasText(rule.getShardColumn())) {
            where = "(" + where + ") and " + shardCondition(where, rule, shardRange, table);
        }
        return where;
    }

    private static String shardCondition(String baseWhere, TableRule rule, ShardRange shardRange, String table) {
        if (shardRange.getStrategy() == ShardRange.Strategy.MOD) {
            return "MOD(" + rule.getShardColumn() + ", " + shardRange.getModulus() + ") = "
                    + shardRange.getRemainder();
        }
        if (shardRange.getStrategy() == ShardRange.Strategy.INTERVAL) {
            return rule.getShardColumn() + " >= " + shardLiteral(rule.getShardType(), shardRange.getFrom())
                    + " and " + rule.getShardColumn() + " < " + shardLiteral(rule.getShardType(), shardRange.getTo());
        }
        if (shardRange.getStrategy() == ShardRange.Strategy.OFFSET) {
            if (!StringUtils.hasText(table)) {
                throw new IllegalArgumentException("OFFSET 分片必须提供表名");
            }
            return rule.getShardColumn() + " in (select " + rule.getShardColumn()
                    + " from (select " + rule.getShardColumn()
                    + " from " + table
                    + " where " + baseWhere
                    + " order by " + rule.getShardColumn()
                    + " limit " + shardRange.getLimit()
                    + " offset " + shardRange.getOffset()
                    + ") shard_window)";
        }
        return rule.getShardColumn() + " between "
                + shardLiteral(rule.getShardType(), shardRange.getFrom())
                + " and "
                + shardLiteral(rule.getShardType(), shardRange.getTo());
    }

    private static String shardLiteral(ShardType shardType, String value) {
        if (shardType == null || shardType == ShardType.NUMBER) {
            return value;
        }
        if (shardType == ShardType.DATE || shardType == ShardType.DATE_INTERVAL) {
            return "DATE '" + value + "'";
        }
        if (shardType == ShardType.TIME || shardType == ShardType.TIME_INTERVAL) {
            return "TIME '" + value + "'";
        }
        return "TIMESTAMP '" + value + "'";
    }

    public static String columns(List<String> fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(fields.get(i));
        }
        return builder.toString();
    }

    public static List<Object> orderedValues(Map<String, Object> row, List<String> fields) {
        List<Object> values = new ArrayList<Object>();
        for (String field : fields) {
            Object value = row.get(field);
            if (value == null) {
                value = row.get(field.toUpperCase());
            }
            values.add(value);
        }
        return values;
    }
}
