package com.example.validator.checker;

import com.example.validator.common.CheckType;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.domain.CheckResult;
import com.example.validator.domain.QueryResult;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import java.util.List;

/**
 * 可插拔核验器接口。
 *
 * <p>职责：定义所有 Checker 插件必须实现的任务规划与结果比对能力。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
public interface ValidationChecker {
    /**
     * 获取 Checker 类型。
     *
     * @return Checker 枚举类型
     */
    CheckType getType();

    /**
     * 判断当前表规则是否满足本 Checker 的字段依赖。
     *
     * @param tableRule 表级核验规则
     * @return true 表示可以生成任务，false 表示缺少必要配置
     */
    boolean support(TableRule tableRule);

    /**
     * 根据数据库配对和表规则生成核验任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param tableRule 表级核验规则
     * @return 核验任务列表
     */
    List<ValidationTask> plan(ValidatorProperties.ComparePair pair, TableRule tableRule);

    /**
     * 比较源端与目标端 SQL 查询结果。
     *
     * @param sourceResult 源端查询结果
     * @param targetResult 目标端查询结果
     * @param tableRule 表级核验规则
     * @return 核验结果
     */
    CheckResult compare(QueryResult sourceResult, QueryResult targetResult, TableRule tableRule);
}
