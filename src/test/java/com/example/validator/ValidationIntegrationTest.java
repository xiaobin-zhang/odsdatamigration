package com.example.validator;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.validator.common.TaskStatus;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.config.ValidatorProperties.SqlDialect;
import com.example.validator.csv.CsvRuleParser;
import com.example.validator.datasource.NamedDataSourceManager;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ProgressSnapshot;
import com.example.validator.domain.ValidationBatch;
import com.example.validator.domain.ValidationTask;
import com.example.validator.executor.ValidationExecutor;
import com.example.validator.mapper.ValidationBatchMapper;
import com.example.validator.mapper.ValidationTaskMapper;
import com.example.validator.planner.ValidationPlanner;
import com.example.validator.service.ValidationJobService;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 集成核验测试。
 *
 * <p>职责：使用 H2 模拟 TDSQL 和 OceanBase，验证完整任务规划、执行、失败识别和断点续跑。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@SpringBootTest
@ActiveProfiles("test")
class ValidationIntegrationTest {
    @Autowired
    private NamedDataSourceManager dataSourceManager;
    @Autowired
    private CsvRuleParser parser;
    @Autowired
    private ValidatorProperties properties;
    @Autowired
    private ValidationPlanner planner;
    @Autowired
    private ValidationExecutor executor;
    @Autowired
    private ValidationTaskMapper taskMapper;
    @Autowired
    private ValidationBatchMapper batchMapper;
    @Autowired
    private ValidationJobService jobService;

    /**
     * 每个用例执行前初始化源端、目标端和元数据库任务表。
     */
    @BeforeEach
    void setUp() {
        initDb("tdsql_01", "schema-tdsql.sql", "data-tdsql.sql");
        initDb("ob_01", "schema-ob.sql", "data-ob.sql");
        taskMapper.delete(new QueryWrapper<ValidationTask>());
        batchMapper.delete(new QueryWrapper<ValidationBatch>());
    }

    /**
     * 验证源端与目标端数据一致时，所有核验任务均通过。
     */
    @Test
    void fullValidationPassesOnH2() {
        List<TableRule> rules = parser.parse("classpath:validation_tables_test.csv");
        List<ValidationTask> tasks = planner.plan(properties.getComparePairs().get(0), rules);
        Map<String, TableRule> ruleIndex = planner.indexRulesByTaskKey(rules);
        String batchId = executor.createBatchAndRun(tasks, ruleIndex);

        List<ValidationTask> saved = taskMapper.selectList(new QueryWrapper<ValidationTask>().eq("batch_id", batchId));
        assertEquals(16, saved.size());
        assertTrue(saved.stream().allMatch(task -> task.getStatus() == TaskStatus.PASS), taskSummaries(saved));
        ProgressSnapshot progress = jobService.progress(batchId);
        assertEquals(100.0D, progress.getProgressPercent());
        assertEquals(16, progress.getTotalCount());
        assertEquals(16, progress.getPassCount());
    }

    /**
     * 验证目标端数据被修改后，至少一个核验任务会失败。
     */
    @Test
    void diffDataProducesFailTask() {
        org.springframework.jdbc.core.JdbcTemplate ob = new org.springframework.jdbc.core.JdbcTemplate(dataSourceManager.getRequired("ob_01"));
        ob.update("update t_order set remark='不一致' where order_id=3");

        List<TableRule> rules = parser.parse("classpath:validation_tables_test.csv");
        String batchId = executor.createBatchAndRun(planner.plan(properties.getComparePairs().get(0), rules), planner.indexRulesByTaskKey(rules));
        List<ValidationTask> saved = taskMapper.selectList(new QueryWrapper<ValidationTask>().eq("batch_id", batchId));

        assertTrue(saved.stream().anyMatch(task -> task.getStatus() == TaskStatus.FAIL));
    }

    /**
     * 验证断点续跑时默认跳过已经 PASS 的任务。
     */
    @Test
    void resumeSkipsPassedTasks() {
        List<TableRule> rules = parser.parse("classpath:validation_tables_test.csv");
        String batchId = executor.createBatchAndRun(planner.plan(properties.getComparePairs().get(0), rules), planner.indexRulesByTaskKey(rules));

        ValidationTask first = taskMapper.selectList(new QueryWrapper<ValidationTask>().eq("batch_id", batchId)).get(0);
        String summary = first.getResultSummary();
        executor.runBatch(batchId, planner.indexRulesByTaskKey(rules));
        ValidationTask afterResume = taskMapper.selectById(first.getId());

        assertEquals(TaskStatus.PASS, afterResume.getStatus());
        assertEquals(summary, afterResume.getResultSummary());
    }

    /**
     * 验证暂停和恢复只改变批次状态，不会清空已生成任务。
     */
    @Test
    void pauseAndResumeChangeBatchStatus() {
        List<TableRule> rules = parser.parse("classpath:validation_tables_test.csv");
        String batchId = executor.createBatchAndRun(planner.plan(properties.getComparePairs().get(0), rules), planner.indexRulesByTaskKey(rules));

        jobService.pause(batchId);
        assertEquals(com.example.validator.common.BatchStatus.COMPLETED, jobService.progress(batchId).getStatus());
        jobService.resume(batchId);
        assertEquals(16, jobService.progress(batchId).getTotalCount());
    }

    @Test
    void safetyViolationSkipsTaskAndDoesNotRetry() {
        properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.MYSQL);
        try {
            ValidationTask task = new ValidationTask();
            task.setPairName("db1_compare");
            task.setSourceName("tdsql_01");
            task.setTargetName("ob_01");
            task.setSourceTable("t_order");
            task.setTargetTable("t_order");
            task.setCheckType(com.example.validator.common.CheckType.ROW_COUNT);
            task.setShardNo(0);
            task.setSourceSql("select count(*) as total_count from t_order where 1=1");
            task.setTargetSql("select * from missing_target_table");

            String batchId = executor.createBatchAndRun(Collections.singletonList(task), Collections.emptyMap());
            ValidationTask saved = taskMapper.selectList(new QueryWrapper<ValidationTask>().eq("batch_id", batchId)).get(0);

            assertEquals(TaskStatus.SKIPPED, saved.getStatus());
            assertEquals(Integer.valueOf(0), saved.getRetryCount());
            assertTrue(saved.getResultSummary().contains("SQL safety guard skipped task"));
        } finally {
            properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.H2);
        }
    }

    private void initDb(String datasourceName, String schema, String data) {
        DataSource dataSource = dataSourceManager.getRequired(datasourceName);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(schema));
        populator.addScript(new ClassPathResource(data));
        populator.execute(dataSource);
    }

    private String taskSummaries(List<ValidationTask> tasks) {
        return tasks.stream()
                .map(task -> task.getSourceTable() + "|" + task.getCheckType() + "|" + task.getShardNo()
                        + "|" + task.getStatus() + "|" + task.getErrorMessage() + "|" + task.getResultSummary())
                .collect(Collectors.joining("\n"));
    }
}
