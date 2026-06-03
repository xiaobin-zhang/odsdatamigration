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
 * 排序抽样核验器。
 *
 * <p>职责：按配置排序字段取前 N 条样本，并比较源端与目标端样本行是否一致。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Component
public class OrderSampleChecker extends AbstractValidationChecker {
    /**
     * 获取当前 Checker 类型。
     *
     * @return ORDER_SAMPLE
     */
    public CheckType getType() { return CheckType.ORDER_SAMPLE; }

    /**
     * 判断表规则是否具备排序抽样核验所需配置。
     *
     * @param tableRule 表级核验规则
     * @return true 表示排序字段和比对字段均已配置
     */
    public boolean support(TableRule tableRule) {
        return !tableRule.getOrderFields().isEmpty() && !tableRule.getCompareFields().isEmpty();
    }

    /**
     * 生成排序抽样核验任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param tableRule 表级核验规则
     * @return 排序抽样核验任务列表
     */
    public List<ValidationTask> plan(ValidatorProperties.ComparePair pair, final TableRule tableRule) {
        return buildTasks(pair, tableRule, (table, shardRange) ->
                "select " + SqlBuilder.columns(tableRule.getCompareFields())
                        + " from " + table
                        + " where " + SqlBuilder.whereWithShard(tableRule.getSampleWhere(), tableRule, shardRange)
                        + " order by " + SqlBuilder.columns(tableRule.getOrderFields())
                        + " limit " + tableRule.getSampleLimit());
    }

    /**
     * 比较排序抽样结果。
     *
     * @param sourceResult 源端排序抽样结果
     * @param targetResult 目标端排序抽样结果
     * @param tableRule 表级核验规则
     * @return 样本完全一致返回 PASS，否则返回 FAIL
     */
    public CheckResult compare(QueryResult sourceResult, QueryResult targetResult, TableRule tableRule) {
        // 排序抽样比较的是“同样排序规则下前 N 条记录是否完全一致”，可以暴露排序规则、字符集和时间精度差异。
        return sourceResult.getRows().equals(targetResult.getRows())
                ? CheckResult.pass("排序抽样一致")
                : CheckResult.fail("排序抽样不一致, sourceRows=" + sourceResult.getRows() + ", targetRows=" + targetResult.getRows());
    }
}
