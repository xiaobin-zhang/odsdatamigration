package com.example.validator.checker;

import com.example.validator.common.CheckType;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.domain.CheckResult;
import com.example.validator.domain.QueryResult;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 日期分组核验器。
 *
 * <p>职责：按日期字段聚合统计每天数据量，并比较源端与目标端分组结果。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Component
public class DateGroupChecker extends AbstractValidationChecker {
    /**
     * 获取当前 Checker 类型。
     *
     * @return DATE_GROUP
     */
    public CheckType getType() { return CheckType.DATE_GROUP; }

    /**
     * 判断表规则是否具备日期分组核验所需配置。
     *
     * @param tableRule 表级核验规则
     * @return true 表示配置了日期字段
     */
    public boolean support(TableRule tableRule) {
        return StringUtils.hasText(tableRule.getDateField());
    }

    /**
     * 生成日期分组核验任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param tableRule 表级核验规则
     * @return 日期分组核验任务列表
     */
    public List<ValidationTask> plan(ValidatorProperties.ComparePair pair, final TableRule tableRule) {
        return buildTasks(pair, tableRule, (table, shardRange) ->
                "select formatdatetime(" + tableRule.getDateField() + ", 'yyyy-MM-dd') as date_bucket, count(*) as cnt from "
                        + table + " where " + SqlBuilder.whereWithShard(tableRule.getWhereClause(), tableRule, shardRange)
                        + " group by formatdatetime(" + tableRule.getDateField() + ", 'yyyy-MM-dd') order by date_bucket");
    }

    /**
     * 比较日期分组统计结果。
     *
     * @param sourceResult 源端日期分组结果
     * @param targetResult 目标端日期分组结果
     * @param tableRule 表级核验规则
     * @return 分组完全一致返回 PASS，否则返回 FAIL
     */
    public CheckResult compare(QueryResult sourceResult, QueryResult targetResult, TableRule tableRule) {
        return sourceResult.getRows().equals(targetResult.getRows())
                ? CheckResult.pass("日期分组一致")
                : CheckResult.fail("日期分组不一致, sourceRows=" + sourceResult.getRows() + ", targetRows=" + targetResult.getRows());
    }
}
