package com.example.validator.checker;

import com.example.validator.config.ValidatorProperties;
import com.example.validator.domain.ShardRange;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import java.util.ArrayList;
import java.util.List;

/**
 * Checker 抽象基类。
 *
 * <p>职责：封装普通任务和分片任务的通用生成逻辑，具体 Checker 只需提供 SQL 生成方式。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
public abstract class AbstractValidationChecker implements ValidationChecker {
    /**
     * 根据表规则生成普通任务或分片任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param rule 表级核验规则
     * @param sqlFactory SQL 生成回调
     * @return 核验任务列表
     */
    protected List<ValidationTask> buildTasks(ValidatorProperties.ComparePair pair, TableRule rule, SqlFactory sqlFactory) {
        List<ValidationTask> tasks = new ArrayList<ValidationTask>();
        if (rule.getShardRanges().isEmpty()) {
            tasks.add(buildTask(pair, rule, null, 0, sqlFactory));
            return tasks;
        }
        int shardNo = 0;
        for (ShardRange shardRange : rule.getShardRanges()) {
            tasks.add(buildTask(pair, rule, shardRange, shardNo++, sqlFactory));
        }
        return tasks;
    }

    private ValidationTask buildTask(ValidatorProperties.ComparePair pair, TableRule rule, ShardRange shardRange,
                                     int shardNo, SqlFactory sqlFactory) {
        ValidationTask task = new ValidationTask();
        task.setPairName(pair.getName());
        task.setSourceName(pair.getSource());
        task.setTargetName(pair.getTarget());
        task.setSourceTable(rule.getSourceTable());
        task.setTargetTable(rule.getTargetTable());
        task.setCheckType(getType());
        task.setShardNo(shardNo);
        task.setSourceSql(sqlFactory.sql(rule.getSourceTable(), shardRange));
        task.setTargetSql(sqlFactory.sql(rule.getTargetTable(), shardRange));
        return task;
    }

    /**
     * Checker SQL 生成回调。
     *
     * <p>职责：根据表名和分片范围生成源端或目标端 SQL。</p>
     *
     * @author Codex
     * @since 2026-06-03
     */
    protected interface SqlFactory {
        /**
         * 生成核验 SQL。
         *
         * @param table 当前数据源侧表名
         * @param shardRange 当前分片范围，非分片任务可为空
         * @return 核验 SQL
         */
        String sql(String table, ShardRange shardRange);
    }
}
