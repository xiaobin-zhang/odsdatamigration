package com.example.validator.checker;

import com.example.validator.common.CheckType;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.domain.CheckResult;
import com.example.validator.domain.QueryResult;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 空值数量核验器。
 *
 * <p>职责：统计配置字段的 NULL 数量，并比较源端与目标端是否一致。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@Component
public class NullCountChecker extends AbstractValidationChecker {
    /**
     * 获取当前 Checker 类型。
     *
     * @return NULL_COUNT
     */
    public CheckType getType() { return CheckType.NULL_COUNT; }

    /**
     * 判断表规则是否具备空值核验所需配置。
     *
     * @param tableRule 表级核验规则
     * @return true 表示至少配置了一个空值统计字段
     */
    public boolean support(TableRule tableRule) {
        return !tableRule.getNullFields().isEmpty();
    }

    /**
     * 生成空值数量核验任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param tableRule 表级核验规则
     * @return 空值数量核验任务列表
     */
    public List<ValidationTask> plan(ValidatorProperties.ComparePair pair, final TableRule tableRule) {
        return buildTasks(pair, tableRule, (datasourceName, table, shardRange) -> {
            StringBuilder sql = new StringBuilder("select ");
            for (int i = 0; i < tableRule.getNullFields().size(); i++) {
                String field = tableRule.getNullFields().get(i);
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("sum(case when ").append(field).append(" is null then 1 else 0 end) as ").append(field).append("_null");
            }
            return sql.append(" from ").append(table)
                    .append(" where ").append(SqlBuilder.whereWithShard(tableRule.getWhereClause(), tableRule, shardRange, table))
                    .toString();
        });
    }

    /**
     * 比较字段空值数量。
     *
     * @param sourceResult 源端空值统计结果
     * @param targetResult 目标端空值统计结果
     * @param tableRule 表级核验规则
     * @return 空值数量一致返回 PASS，否则返回 FAIL
     */
    public CheckResult compare(QueryResult sourceResult, QueryResult targetResult, TableRule tableRule) {
        return ResultComparator.rowsEqual(sourceResult, targetResult, nullCountColumns(tableRule))
                ? CheckResult.pass("空值数量一致")
                : CheckResult.fail("空值数量不一致, sourceRows=" + sourceResult.getRows() + ", targetRows=" + targetResult.getRows());
    }

    private List<String> nullCountColumns(TableRule tableRule) {
        java.util.ArrayList<String> columns = new java.util.ArrayList<String>();
        for (String field : tableRule.getNullFields()) {
            columns.add(field + "_null");
        }
        return columns;
    }
}
