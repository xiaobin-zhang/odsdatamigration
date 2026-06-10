package com.example.validator.checker;

import com.example.validator.common.CheckType;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.domain.CheckResult;
import com.example.validator.domain.QueryResult;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 金额汇总核验器。
 *
 * <p>职责：对配置的金额字段执行 sum、非空数和空值数统计，并按容差比较结果。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@Component
public class AmountSumChecker extends AbstractValidationChecker {
    /**
     * 获取当前 Checker 类型。
     *
     * @return AMOUNT_SUM
     */
    public CheckType getType() { return CheckType.AMOUNT_SUM; }

    /**
     * 判断表规则是否具备金额汇总核验所需配置。
     *
     * @param tableRule 表级核验规则
     * @return true 表示至少配置了一个金额字段
     */
    public boolean support(TableRule tableRule) {
        return !tableRule.getAmountFields().isEmpty();
    }

    /**
     * 生成金额汇总核验任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param tableRule 表级核验规则
     * @return 金额汇总核验任务列表
     */
    public List<ValidationTask> plan(ValidatorProperties.ComparePair pair, final TableRule tableRule) {
        return buildTasks(pair, tableRule, (datasourceName, table, shardRange) -> {
            StringBuilder select = new StringBuilder("select ");
            for (int i = 0; i < tableRule.getAmountFields().size(); i++) {
                String field = tableRule.getAmountFields().get(i);
                if (i > 0) {
                    select.append(", ");
                }
                select.append("sum(").append(field).append(") as ").append(field).append("_sum")
                        .append(", count(").append(field).append(") as ").append(field).append("_non_null")
                        .append(", count(*) - count(").append(field).append(") as ").append(field).append("_null");
            }
            return select.append(" from ").append(table)
                    .append(" where ").append(SqlBuilder.whereWithShard(tableRule.getWhereClause(), tableRule, shardRange, table))
                    .toString();
        });
    }

    /**
     * 比较金额字段的汇总值、非空数量和空值数量。
     *
     * @param sourceResult 源端金额聚合结果
     * @param targetResult 目标端金额聚合结果
     * @param tableRule 表级核验规则，包含金额容差配置
     * @return 全部字段一致返回 PASS，否则返回 FAIL
     */
    public CheckResult compare(QueryResult sourceResult, QueryResult targetResult, TableRule tableRule) {
        Map<String, Object> source = sourceResult.getRows().get(0);
        Map<String, Object> target = targetResult.getRows().get(0);
        for (String field : tableRule.getAmountFields()) {
            String sumKey = field + "_sum";
            BigDecimal sourceSum = decimal(get(source, sumKey));
            BigDecimal targetSum = decimal(get(target, sumKey));
            // 金额字段允许配置容差，避免不同数据库 DECIMAL/函数实现导致极小误差误报。
            if (sourceSum.subtract(targetSum).abs().compareTo(tableRule.getAmountTolerance()) > 0) {
                return CheckResult.fail("金额汇总不一致, field=" + field + ", source=" + sourceSum + ", target=" + targetSum);
            }
            if (!String.valueOf(get(source, field + "_non_null")).equals(String.valueOf(get(target, field + "_non_null")))
                    || !String.valueOf(get(source, field + "_null")).equals(String.valueOf(get(target, field + "_null")))) {
                return CheckResult.fail("金额字段空值/非空数量不一致, field=" + field);
            }
        }
        return CheckResult.pass("金额汇总一致");
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private Object get(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase());
    }
}
