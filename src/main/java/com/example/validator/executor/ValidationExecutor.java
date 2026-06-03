package com.example.validator.executor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.validator.checker.CheckerRegistry;
import com.example.validator.checker.ValidationChecker;
import com.example.validator.common.TaskStatus;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.datasource.QueryExecutor;
import com.example.validator.domain.CheckResult;
import com.example.validator.domain.QueryResult;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import com.example.validator.mapper.ValidationTaskMapper;
import com.example.validator.service.ValidationJobService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;

/**
 * 核验任务执行器。
 *
 * <p>职责：将规划任务落库，按配置并行执行，并维护任务状态以支持断点续跑。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Component
public class ValidationExecutor {
    private final ValidatorProperties properties;
    private final QueryExecutor queryExecutor;
    private final CheckerRegistry checkerRegistry;
    private final ValidationTaskMapper taskMapper;
    private final ValidationJobService jobService;

    /**
     * 创建核验任务执行器。
     *
     * @param properties 核验程序配置
     * @param queryExecutor SQL 查询执行器
     * @param checkerRegistry Checker 插件注册表
     * @param taskMapper 任务持久化 Mapper
     * @param jobService 作业控制服务
     */
    public ValidationExecutor(ValidatorProperties properties, QueryExecutor queryExecutor,
                              CheckerRegistry checkerRegistry, ValidationTaskMapper taskMapper,
                              ValidationJobService jobService) {
        this.properties = properties;
        this.queryExecutor = queryExecutor;
        this.checkerRegistry = checkerRegistry;
        this.taskMapper = taskMapper;
        this.jobService = jobService;
    }

    /**
     * 创建新批次并执行任务。
     *
     * @param plannedTasks 已规划但尚未落库的任务
     * @param ruleIndex 任务到表规则的索引
     * @return 新生成的批次号
     */
    public String createBatchAndRun(List<ValidationTask> plannedTasks, Map<String, TableRule> ruleIndex) {
        String batchId = UUID.randomUUID().toString();
        createBatch(batchId, plannedTasks);
        runBatch(batchId, ruleIndex);
        return batchId;
    }

    /**
     * 创建批次并落库任务，但不立即执行。
     *
     * @param batchId 批次号
     * @param plannedTasks 已规划但尚未落库的任务
     */
    public void createBatch(String batchId, List<ValidationTask> plannedTasks) {
        jobService.createBatch(batchId, plannedTasks.size());
        for (ValidationTask task : plannedTasks) {
            task.setBatchId(batchId);
            taskMapper.insert(task);
        }
    }

    /**
     * 执行指定批次中的可运行任务。
     *
     * @param batchId 批次号
     * @param ruleIndex 任务到表规则的索引
     */
    public void runBatch(String batchId, Map<String, TableRule> ruleIndex) {
        jobService.markRunning(batchId);
        QueryWrapper<ValidationTask> wrapper = new QueryWrapper<ValidationTask>().eq("batch_id", batchId);
        List<ValidationTask> allTasks = taskMapper.selectList(wrapper);
        List<ValidationTask> runnable = new ArrayList<ValidationTask>();
        for (ValidationTask task : allTasks) {
            if (shouldRun(task)) {
                runnable.add(task);
            }
        }

        int parallelism = Math.max(1, properties.getExecution().getTableParallelism());
        ExecutorService executorService = Executors.newFixedThreadPool(parallelism);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        final AtomicInteger nextIndex = new AtomicInteger(0);
        for (int i = 0; i < parallelism; i++) {
            futures.add(executorService.submit(() -> {
                int index;
                while ((index = nextIndex.getAndIncrement()) < runnable.size()) {
                    jobService.waitIfPaused(batchId);
                    executeOne(runnable.get(index), ruleIndex);
                }
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                throw new IllegalStateException("核验任务执行失败", e);
            }
        }
        executorService.shutdown();
        jobService.markCompleted(batchId);
    }

    private boolean shouldRun(ValidationTask task) {
        // 断点续跑的核心判断：已通过任务默认不重跑，异常/失败/待执行任务会继续执行。
        // 这样程序中断后重新执行同一 batchId 时，不会浪费已经完成的长耗时任务。
        if (properties.isRerunPassed()) {
            return true;
        }
        return task.getStatus() == TaskStatus.PENDING
                || task.getStatus() == TaskStatus.FAIL
                || task.getStatus() == TaskStatus.ERROR
                || task.getStatus() == TaskStatus.RUNNING;
    }

    private void executeOne(ValidationTask task, Map<String, TableRule> ruleIndex) {
        try {
            task.setStatus(TaskStatus.RUNNING);
            taskMapper.updateById(task);
            jobService.refreshProgress(task.getBatchId());

            QueryResult source = queryExecutor.query(task.getSourceName(), task.getSourceSql(), properties.getExecution().getQueryTimeoutSeconds());
            QueryResult target = queryExecutor.query(task.getTargetName(), task.getTargetSql(), properties.getExecution().getQueryTimeoutSeconds());
            ValidationChecker checker = checkerRegistry.getRequired(task.getCheckType());
            TableRule rule = ruleIndex.get(task.getPairName() + "|" + task.getSourceTable() + "|" + task.getCheckType().name());
            CheckResult result = checker.compare(source, target, rule);

            task.setStatus(result.getStatus());
            task.setResultSummary(result.getSummary() + ", sourceCostMs=" + source.getCostMs() + ", targetCostMs=" + target.getCostMs());
            task.setErrorMessage(null);
            taskMapper.updateById(task);
            jobService.refreshProgress(task.getBatchId());
        } catch (Exception e) {
            task.setStatus(TaskStatus.ERROR);
            task.setErrorMessage(e.getMessage());
            taskMapper.updateById(task);
            jobService.refreshProgress(task.getBatchId());
        }
    }
}
