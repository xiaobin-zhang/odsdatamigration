package com.example.validator;

import com.example.validator.checker.CheckerRegistry;
import com.example.validator.checker.ValidationChecker;
import com.example.validator.common.CheckType;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.csv.CsvRuleParser;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import com.example.validator.planner.ValidationPlanner;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核验任务规划器测试。
 *
 * <p>职责：验证 Checker 自动注册、按表选择 Checker 以及缺失必要字段时的配置失败逻辑。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@SpringBootTest
@ActiveProfiles("test")
class ValidationPlannerTest {
    @Autowired
    private CsvRuleParser parser;
    @Autowired
    private ValidatorProperties properties;
    @Autowired
    private ValidationPlanner planner;
    @Autowired
    private CheckerRegistry checkerRegistry;

    /**
     * 验证内置 6 个 Checker 均可被 Spring 自动发现并注册。
     */
    @Test
    void registryDiscoversAllBuiltInCheckers() {
        Map<CheckType, ValidationChecker> checkerMap = checkerRegistry.getCheckerMap();
        for (CheckType type : CheckType.values()) {
            assertTrue(checkerMap.containsKey(type), "missing " + type);
        }
    }

    /**
     * 验证任务规划器只为 CSV 中声明的 Checker 生成任务。
     */
    @Test
    void planOnlyCreatesConfiguredCheckerTasks() {
        List<TableRule> rules = parser.parse("classpath:validation_tables_test.csv");
        ValidatorProperties.ComparePair pair = properties.getComparePairs().get(0);
        List<ValidationTask> tasks = planner.plan(pair, rules);
        Map<String, List<ValidationTask>> byTable = tasks.stream().collect(Collectors.groupingBy(ValidationTask::getSourceTable));

        assertEquals(6, byTable.get("t_order").size());
        assertEquals(2, byTable.get("t_user").size());
        assertEquals(1, byTable.get("t_null_only").size());
        assertFalse(byTable.containsKey("t_log"));
        assertFalse(byTable.containsKey("t_skip"));
    }

    /**
     * 验证启用 Checker 但缺少必要字段时会生成配置错误。
     */
    @Test
    void enabledCheckerMissingRequiredFieldsFails() {
        String csv = String.join(",", CsvRuleParser.REQUIRED_HEADERS) + "\n"
                + "db1_compare,true,t_order,t_order,id,AMOUNT_SUM,1=1,,,,,,1=1,10,,,\n";
        List<TableRule> rules = parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(properties.getComparePairs().get(0), rules));
    }
}
