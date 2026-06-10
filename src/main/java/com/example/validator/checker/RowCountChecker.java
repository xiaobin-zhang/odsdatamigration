package com.example.validator.checker;

import com.example.validator.common.CheckType;
import com.example.validator.common.TaskStatus;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.domain.CheckResult;
import com.example.validator.domain.QueryResult;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据总数核验器。
 *
 * <p>职责：生成 count(*) SQL，并比较源端与目标端的总行数是否一致。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@Component
public class RowCountChecker extends AbstractValidationChecker {
    /**
     * 获取当前 Checker 类型。
     *
     * @return ROW_COUNT
     */
    public CheckType getType() { return CheckType.ROW_COUNT; }

    /**
     * 判断表规则是否具备行数核验所需配置。
     *
     * @param tableRule 表级核验规则
     * @return true 表示源表和目标表均已配置
     */
    public boolean support(TableRule tableRule) {
        return StringUtils.hasText(tableRule.getSourceTable()) && StringUtils.hasText(tableRule.getTargetTable());
    }

    /**
     * 生成行数核验任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param tableRule 表级核验规则
     * @return 行数核验任务列表
     */
    public List<ValidationTask> plan(ValidatorProperties.ComparePair pair, final TableRule tableRule) {
        return buildTasks(pair, tableRule, (datasourceName, table, shardRange) ->
                "select count(*) as total_count from " + table + " where " + SqlBuilder.whereWithShard(tableRule.getWhereClause(), tableRule, shardRange, table));
    }

    /**
     * 比较源端与目标端行数。
     *
     * @param sourceResult 源端 count 查询结果
     * @param targetResult 目标端 count 查询结果
     * @param tableRule 表级核验规则
     * @return 行数一致返回 PASS，否则返回 FAIL
     */
    public CheckResult compare(QueryResult sourceResult, QueryResult targetResult, TableRule tableRule) {
        Object source = firstValue(sourceResult);
        Object target = firstValue(targetResult);
        return String.valueOf(source).equals(String.valueOf(target))
                ? CheckResult.pass("行数一致: " + source)
                : CheckResult.fail("行数不一致, source=" + source + ", target=" + target);
    }

    private Object firstValue(QueryResult result) {
        Map<String, Object> row = result.getRows().get(0);
        return row.values().iterator().next();
    }
}
