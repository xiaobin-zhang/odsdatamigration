package com.example.validator;

import com.example.validator.checker.CheckerRegistry;
import com.example.validator.checker.ValidationChecker;
import com.example.validator.common.CheckType;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.config.ValidatorProperties.SqlDialect;
import com.example.validator.csv.CsvRuleParser;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import com.example.validator.planner.ValidationPlanner;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void registryDiscoversAllBuiltInCheckers() {
        Map<CheckType, ValidationChecker> checkerMap = checkerRegistry.getCheckerMap();
        for (CheckType type : CheckType.values()) {
            assertTrue(checkerMap.containsKey(type), "missing " + type);
        }
    }

    @Test
    void planOnlyCreatesConfiguredCheckerTasks() {
        List<TableRule> rules = parser.parse("classpath:validation_tables_test.csv");
        ValidatorProperties.ComparePair pair = properties.getComparePairs().get(0);
        List<ValidationTask> tasks = planner.plan(pair, rules);
        Map<String, List<ValidationTask>> byTable = tasks.stream().collect(Collectors.groupingBy(ValidationTask::getSourceTable));

        assertEquals(12, byTable.get("t_order").size());
        assertEquals(2, byTable.get("t_user").size());
        assertEquals(1, byTable.get("t_null_only").size());
        assertEquals(1, byTable.get("t_composite").size());
        assertFalse(byTable.containsKey("t_log"));
        assertFalse(byTable.containsKey("t_skip"));
    }

    @Test
    void enabledCheckerMissingRequiredFieldsFails() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,AMOUNT_SUM,1=1,,,,,,1=1,10,,,,0.00\n";
        List<TableRule> rules = parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(properties.getComparePairs().get(0), rules));
    }

    @Test
    void md5SampleUsesCompositePrimaryKeyForOrdering() {
        String csv = header()
                + "db1_compare,true,t_composite,t_composite,\"tenant_id,order_id\",MD5_SAMPLE,1=1,,,,,\"tenant_id,order_id,status\",1=1,10,,,,0.00\n";
        ValidationTask task = planner.plan(properties.getComparePairs().get(0),
                parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)))).get(0);

        assertTrue(task.getSourceSql().contains("select tenant_id, order_id, status"));
        assertTrue(task.getSourceSql().contains("order by tenant_id, order_id"));
    }

    @Test
    void shardSqlUsesTypedDatetimeLiteral() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,create_time,DATETIME,\"2026-01-01 00:00:00~2026-01-31 23:59:59\",0.00\n";
        ValidationTask task = planner.plan(properties.getComparePairs().get(0),
                parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)))).get(0);

        assertTrue(task.getSourceSql().contains("create_time between TIMESTAMP '2026-01-01 00:00:00' and TIMESTAMP '2026-01-31 23:59:59'"));
    }

    @Test
    void numberModShardSqlUsesModPredicate() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,order_id,ROW_COUNT,1=1,,,,,,1=1,10,order_id,NUMBER_MOD,\"2\",0.00\n";
        List<ValidationTask> tasks = planner.plan(properties.getComparePairs().get(0),
                parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        assertEquals(2, tasks.size());
        assertTrue(tasks.get(0).getSourceSql().contains("MOD(order_id, 2) = 0"));
        assertTrue(tasks.get(1).getSourceSql().contains("MOD(order_id, 2) = 1"));
    }

    @Test
    void intervalShardSqlUsesHalfOpenPredicate() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,order_id,ROW_COUNT,1=1,,,,,,1=1,10,create_time,DATETIME_INTERVAL,\"2026-01-01 00:00:00~2026-01-03 00:00:00~1d\",0.00\n";
        List<ValidationTask> tasks = planner.plan(properties.getComparePairs().get(0),
                parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        assertEquals(2, tasks.size());
        assertTrue(tasks.get(0).getSourceSql().contains("create_time >= TIMESTAMP '2026-01-01 00:00:00'"));
        assertTrue(tasks.get(0).getSourceSql().contains("create_time < TIMESTAMP '2026-01-02 00:00:00'"));
    }

    @Test
    void offsetShardSqlUsesSourceCountLimitAndOffset() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,order_id,ROW_COUNT,1=1,,,,,,1=1,10,order_id,OFFSET,\"2\",0.00\n";
        List<ValidationTask> tasks = planner.plan(properties.getComparePairs().get(0),
                parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        assertEquals(2, tasks.size());
        assertFalse(tasks.get(0).getSourceSql().contains(" in (select "));
        assertTrue(tasks.get(0).getSourceSql().contains("from (select * from t_order where 1=1 order by order_id limit 2 offset 0) shard_rows"));
        assertTrue(tasks.get(1).getSourceSql().contains("from (select * from t_order where 1=1 order by order_id limit 2 offset 2) shard_rows"));
    }

    @Test
    void offsetShardForSampleCheckerUsesSampleWhereForWindow() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,order_id,MD5_SAMPLE,1=1,,,,,\"order_id,user_id,status\",order_id <= 2,10,order_id,OFFSET,\"2\",0.00\n";
        List<ValidationTask> tasks = planner.plan(properties.getComparePairs().get(0),
                parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        assertEquals(2, tasks.size());
        assertTrue(tasks.get(0).getSourceSql().contains("from (select * from t_order where order_id <= 2 order by order_id limit 1 offset 0) shard_rows"));
        assertTrue(tasks.get(1).getSourceSql().contains("from (select * from t_order where order_id <= 2 order by order_id limit 1 offset 1) shard_rows"));
    }

    @Test
    void offsetShardForAggregateCheckerUsesWhereClauseForWindow() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,order_id,ROW_COUNT,order_id <= 2,,,,,,1=1,10,order_id,OFFSET,\"2\",0.00\n";
        List<ValidationTask> tasks = planner.plan(properties.getComparePairs().get(0),
                parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        assertEquals(2, tasks.size());
        assertTrue(tasks.get(0).getSourceSql().contains("from (select * from t_order where order_id <= 2 order by order_id limit 1 offset 0) shard_rows"));
        assertTrue(tasks.get(1).getSourceSql().contains("from (select * from t_order where order_id <= 2 order by order_id limit 1 offset 1) shard_rows"));
    }

    @Test
    void offsetShardOnDuplicateShardColumnUsesDerivedRowsInsteadOfInPredicate() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,order_id,ROW_COUNT,1=1,,,,,,1=1,10,status,OFFSET,\"2\",0.00\n";
        List<ValidationTask> tasks = planner.plan(properties.getComparePairs().get(0),
                parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        assertEquals(2, tasks.size());
        assertFalse(tasks.get(0).getSourceSql().contains(" in (select "));
        assertTrue(tasks.get(0).getSourceSql().contains("from (select * from t_order where 1=1 order by status, order_id limit 2 offset 0) shard_rows"));
    }

    @Test
    void largeTableOffsetShardPlanningFails() {
        properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.MYSQL);
        try {
            String csv = header()
                    + "db1_compare,true,t_order,t_order,order_id,ROW_COUNT,1=1,,,,,,1=1,10,order_id,OFFSET,\"2\",0.00\n";
            List<TableRule> rules = parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> planner.plan(properties.getComparePairs().get(0), rules));

            assertTrue(ex.getMessage().contains("Large table cannot use OFFSET shard"));
        } finally {
            properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.H2);
        }
    }

    @Test
    void dateGroupSqlUsesConfiguredDatasourceDialects() {
        properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.TDSQL_MYSQL);
        properties.getDatasources().get("ob_01").setDialect(SqlDialect.OCEANBASE_ORACLE);
        try {
            String csv = header()
                    + "db1_compare,true,t_order,t_order,id,DATE_GROUP,1=1,,create_time,,,,1=1,10,,,,0.00\n";
            List<TableRule> rules = parser.parse(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
            ValidationTask task = planner.plan(properties.getComparePairs().get(0), rules).get(0);

            assertTrue(task.getSourceSql().contains("date_format(create_time, '%Y-%m-%d')"));
            assertTrue(task.getTargetSql().contains("to_char(create_time, 'YYYY-MM-DD')"));
        } finally {
            properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.AUTO);
            properties.getDatasources().get("ob_01").setDialect(SqlDialect.AUTO);
        }
    }

    private String header() {
        return String.join(",", CsvRuleParser.REQUIRED_HEADERS) + "\n";
    }
}
