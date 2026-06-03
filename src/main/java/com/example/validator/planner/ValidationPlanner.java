package com.example.validator.planner;

import com.example.validator.checker.CheckerRegistry;
import com.example.validator.checker.ValidationChecker;
import com.example.validator.common.CheckType;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 核验任务规划器。
 *
 * <p>职责：根据数据库配对和 CSV 表规则选择 Checker 插件，并生成待执行任务。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Component
public class ValidationPlanner {
    private final CheckerRegistry checkerRegistry;

    /**
     * 创建任务规划器。
     *
     * @param checkerRegistry Checker 插件注册表
     */
    public ValidationPlanner(CheckerRegistry checkerRegistry) {
        this.checkerRegistry = checkerRegistry;
    }

    /**
     * 为一个数据库配对生成核验任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param rules CSV 解析得到的表规则列表
     * @return 待执行核验任务列表
     */
    public List<ValidationTask> plan(ValidatorProperties.ComparePair pair, List<TableRule> rules) {
        List<ValidationTask> tasks = new ArrayList<ValidationTask>();
        for (TableRule rule : rules) {
            if (!rule.isEnabled() || !pair.getName().equals(rule.getPairName())) {
                continue;
            }
            if (rule.getCheckers().isEmpty()) {
                continue;
            }
            for (CheckType checkType : rule.getCheckers()) {
                // 每张表只按 CSV 中声明的 Checker 生成任务；未声明的 Checker 完全不会执行 SQL。
                ValidationChecker checker = checkerRegistry.getRequired(checkType);
                if (!checker.support(rule)) {
                    throw new IllegalArgumentException("表 " + rule.getSourceTable() + " 启用了 " + checkType + " 但缺少必需字段");
                }
                tasks.addAll(checker.plan(pair, rule));
            }
        }
        return tasks;
    }

    /**
     * 构建任务到表规则的索引。
     *
     * @param rules CSV 表规则列表
     * @return key 为 pairName|sourceTable|checkType 的规则索引
     */
    public Map<String, TableRule> indexRulesByTaskKey(List<TableRule> rules) {
        Map<String, TableRule> index = new LinkedHashMap<String, TableRule>();
        for (TableRule rule : rules) {
            for (CheckType checkType : rule.getCheckers()) {
                index.put(rule.getPairName() + "|" + rule.getSourceTable() + "|" + checkType.name(), rule);
            }
        }
        return index;
    }
}
