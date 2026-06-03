package com.example.validator.checker;

import com.example.validator.domain.ShardRange;
import com.example.validator.domain.TableRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 核验 SQL 构造辅助类。
 *
 * <p>职责：提供 where 分片拼接、字段列表拼接等通用 SQL 片段生成能力。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
public final class SqlBuilder {
    private SqlBuilder() {
    }

    /**
     * 拼接基础过滤条件与分片过滤条件。
     *
     * @param baseWhere CSV 配置的基础 where 条件
     * @param rule 表级核验规则
     * @param shardRange 当前分片范围，非分片任务可为空
     * @return 可直接拼接到 SQL 的 where 条件
     */
    public static String whereWithShard(String baseWhere, TableRule rule, ShardRange shardRange) {
        String where = StringUtils.hasText(baseWhere) ? baseWhere : "1=1";
        if (shardRange != null && StringUtils.hasText(rule.getShardColumn())) {
            // 分片条件统一追加到 where 后面，使大表可以按主键/分区键拆小任务执行，降低单条 SQL 压力。
            where = "(" + where + ") and " + rule.getShardColumn() + " between " + shardRange.getFrom() + " and " + shardRange.getTo();
        }
        return where;
    }

    /**
     * 将字段集合拼接成 SQL select/order by 字段列表。
     *
     * @param fields 字段名列表
     * @return 逗号分隔的字段列表
     */
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

    /**
     * 按字段顺序从一行结果中提取值。
     *
     * @param row 查询返回的一行数据
     * @param fields 需要提取的字段顺序
     * @return 按字段顺序排列的值列表
     */
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
