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

    public static String fromWithShard(String table, String baseWhere, TableRule rule, ShardRange shardRange) {
        String where = StringUtils.hasText(baseWhere) ? baseWhere : "1=1";
        if (shardRange != null && shardRange.getStrategy() == ShardRange.Strategy.OFFSET) {
            return "from (select * from " + table
                    + " where " + where
                    + " order by " + offsetOrderColumns(rule)
                    + " limit " + shardRange.getLimit()
                    + " offset " + shardRange.getOffset()
                    + ") shard_rows";
        }
        return "from " + table + " where " + whereWithShard(where, rule, shardRange);
    }

    private static String offsetOrderColumns(TableRule rule) {
        List<String> orderColumns = new ArrayList<String>();
        if (StringUtils.hasText(rule.getShardColumn())) {
            orderColumns.add(rule.getShardColumn());
        }
        for (String primaryKey : rule.getPrimaryKeys()) {
            if (!orderColumns.contains(primaryKey)) {
                orderColumns.add(primaryKey);
            }
        }
        return columns(orderColumns);
    }

    private static String whereWithShard(String baseWhere, TableRule rule, ShardRange shardRange) {
        String where = StringUtils.hasText(baseWhere) ? baseWhere : "1=1";
        if (shardRange != null && StringUtils.hasText(rule.getShardColumn())) {
            where = "(" + where + ") and " + shardCondition(rule, shardRange);
        }
        return where;
    }

    private static String shardCondition(TableRule rule, ShardRange shardRange) {
        if (shardRange.getStrategy() == ShardRange.Strategy.MOD) {
            return "MOD(" + rule.getShardColumn() + ", " + shardRange.getModulus() + ") = "
                    + shardRange.getRemainder();
        }
        if (shardRange.getStrategy() == ShardRange.Strategy.INTERVAL) {
            return rule.getShardColumn() + " >= " + shardLiteral(rule.getShardType(), shardRange.getFrom())
                    + " and " + rule.getShardColumn() + " < " + shardLiteral(rule.getShardType(), shardRange.getTo());
        }
        if (shardRange.getStrategy() == ShardRange.Strategy.OFFSET) {
            throw new IllegalArgumentException("OFFSET 分片必须通过 fromWithShard 生成分片数据集");
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
