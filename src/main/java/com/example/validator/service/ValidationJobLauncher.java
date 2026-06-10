package com.example.validator.service;

import com.example.validator.config.ValidatorProperties;
import com.example.validator.csv.CsvRuleParser;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import com.example.validator.executor.ValidationExecutor;
import com.example.validator.planner.ValidationPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

/**
 * 核验作业启动服务。
 *
 * <p>职责：解析 CSV、生成任务、创建批次，并在后台线程执行核验作业。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@Service
public class ValidationJobLauncher {
    private final ValidatorProperties properties;
    private final CsvRuleParser csvRuleParser;
    private final ValidationPlanner validationPlanner;
    private final ValidationExecutor validationExecutor;
    private final ValidationJobService jobService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 创建作业启动服务。
     *
     * @param properties 核验程序配置
     * @param csvRuleParser CSV 表规则解析器
     * @param validationPlanner 核验任务规划器
     * @param validationExecutor 核验任务执行器
     * @param jobService 作业控制服务
     */
    public ValidationJobLauncher(ValidatorProperties properties, CsvRuleParser csvRuleParser,
                                 ValidationPlanner validationPlanner, ValidationExecutor validationExecutor,
                                 ValidationJobService jobService) {
        this.properties = properties;
        this.csvRuleParser = csvRuleParser;
        this.validationPlanner = validationPlanner;
        this.validationExecutor = validationExecutor;
        this.jobService = jobService;
    }

    /**
     * 创建批次并异步启动核验作业。
     *
     * @return 新生成的批次号
     */
    public String startAsync() {
        List<TableRule> rules = csvRuleParser.parse(properties.getCsvPath());
        List<ValidationTask> tasks = new ArrayList<ValidationTask>();
        for (ValidatorProperties.ComparePair pair : properties.getComparePairs()) {
            if (pair.isEnabled()) {
                tasks.addAll(validationPlanner.plan(pair, rules));
            }
        }
        Map<String, TableRule> ruleIndex = validationPlanner.indexRulesByTaskKey(rules);
        String batchId = UUID.randomUUID().toString();
        validationExecutor.createBatch(batchId, tasks);
        executorService.submit(() -> {
            try {
                validationExecutor.runBatch(batchId, ruleIndex);
            } catch (RuntimeException e) {
                jobService.markFailed(batchId);
            }
        });
        return batchId;
    }
}
