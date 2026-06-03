package com.example.validator.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.validator.common.BatchStatus;
import com.example.validator.common.TaskStatus;
import com.example.validator.domain.ProgressSnapshot;
import com.example.validator.domain.ValidationBatch;
import com.example.validator.domain.ValidationTask;
import com.example.validator.mapper.ValidationBatchMapper;
import com.example.validator.mapper.ValidationTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 核验作业控制服务。
 *
 * <p>职责：维护批次级状态，提供进度查询、暂停、恢复和状态刷新能力。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Service
public class ValidationJobService {
    private final ValidationBatchMapper batchMapper;
    private final ValidationTaskMapper taskMapper;

    /**
     * 创建作业控制服务。
     *
     * @param batchMapper 批次持久化 Mapper
     * @param taskMapper 任务持久化 Mapper
     */
    public ValidationJobService(ValidationBatchMapper batchMapper, ValidationTaskMapper taskMapper) {
        this.batchMapper = batchMapper;
        this.taskMapper = taskMapper;
    }

    /**
     * 创建批次记录。
     *
     * @param batchId 批次号
     * @param totalCount 批次下任务总数
     */
    public void createBatch(String batchId, int totalCount) {
        ValidationBatch batch = new ValidationBatch();
        batch.setBatchId(batchId);
        batch.setStatus(BatchStatus.CREATED);
        batch.setTotalCount(totalCount);
        batch.setPendingCount(totalCount);
        batch.setRunningCount(0);
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setErrorCount(0);
        batch.setSkippedCount(0);
        batch.setStartTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.insert(batch);
    }

    /**
     * 标记批次开始运行。
     *
     * @param batchId 批次号
     */
    public void markRunning(String batchId) {
        ValidationBatch batch = getRequiredBatch(batchId);
        if (batch.getStatus() != BatchStatus.PAUSED) {
            batch.setStatus(BatchStatus.RUNNING);
        }
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    /**
     * 请求暂停批次。
     *
     * @param batchId 批次号
     */
    public void pause(String batchId) {
        ValidationBatch batch = getRequiredBatch(batchId);
        // 暂停只改变批次状态，执行器会在每个任务开始前检查该状态。
        // 已经 RUNNING 的 SQL 不会被 interrupt，保证不会产生半执行状态。
        if (batch.getStatus() == BatchStatus.RUNNING || batch.getStatus() == BatchStatus.CREATED) {
            batch.setStatus(BatchStatus.PAUSED);
            batch.setUpdateTime(LocalDateTime.now());
            batchMapper.updateById(batch);
        }
    }

    /**
     * 恢复暂停中的批次。
     *
     * @param batchId 批次号
     */
    public void resume(String batchId) {
        ValidationBatch batch = getRequiredBatch(batchId);
        if (batch.getStatus() == BatchStatus.PAUSED) {
            batch.setStatus(BatchStatus.RUNNING);
            batch.setUpdateTime(LocalDateTime.now());
            batchMapper.updateById(batch);
        }
    }

    /**
     * 判断批次是否处于暂停状态。
     *
     * @param batchId 批次号
     * @return true 表示暂停中
     */
    public boolean isPaused(String batchId) {
        return getRequiredBatch(batchId).getStatus() == BatchStatus.PAUSED;
    }

    /**
     * 在任务启动前等待暂停结束。
     *
     * @param batchId 批次号
     */
    public void waitIfPaused(String batchId) {
        while (isPaused(batchId)) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待恢复时线程被中断", e);
            }
        }
    }

    /**
     * 刷新批次进度统计和当前运行任务信息。
     *
     * @param batchId 批次号
     */
    public void refreshProgress(String batchId) {
        ProgressSnapshot snapshot = progress(batchId);
        ValidationBatch batch = getRequiredBatch(batchId);
        batch.setTotalCount(snapshot.getTotalCount());
        batch.setPendingCount(snapshot.getPendingCount());
        batch.setRunningCount(snapshot.getRunningCount());
        batch.setPassCount(snapshot.getPassCount());
        batch.setFailCount(snapshot.getFailCount());
        batch.setErrorCount(snapshot.getErrorCount());
        batch.setSkippedCount(snapshot.getSkippedCount());
        batch.setCurrentPairName(snapshot.getCurrentPairName());
        batch.setCurrentSourceTable(snapshot.getCurrentSourceTable());
        batch.setCurrentTargetTable(snapshot.getCurrentTargetTable());
        batch.setCurrentCheckType(snapshot.getCurrentCheckType());
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    /**
     * 标记批次完成。
     *
     * @param batchId 批次号
     */
    public void markCompleted(String batchId) {
        refreshProgress(batchId);
        ValidationBatch batch = getRequiredBatch(batchId);
        batch.setStatus(BatchStatus.COMPLETED);
        batch.setEndTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    /**
     * 查询批次当前进度。
     *
     * @param batchId 批次号
     * @return 进度快照
     */
    public ProgressSnapshot progress(String batchId) {
        ValidationBatch batch = getRequiredBatch(batchId);
        List<ValidationTask> tasks = taskMapper.selectList(new QueryWrapper<ValidationTask>().eq("batch_id", batchId));
        ProgressSnapshot snapshot = new ProgressSnapshot();
        snapshot.setBatchId(batchId);
        snapshot.setStatus(batch.getStatus());
        snapshot.setTotalCount(tasks.size());
        for (ValidationTask task : tasks) {
            count(snapshot, task);
            if (task.getStatus() == TaskStatus.RUNNING) {
                snapshot.setCurrentPairName(task.getPairName());
                snapshot.setCurrentSourceTable(task.getSourceTable());
                snapshot.setCurrentTargetTable(task.getTargetTable());
                snapshot.setCurrentCheckType(task.getCheckType().name());
            }
        }
        int finished = snapshot.getPassCount() + snapshot.getFailCount() + snapshot.getErrorCount() + snapshot.getSkippedCount();
        snapshot.setProgressPercent(snapshot.getTotalCount() == 0 ? 100.0D : finished * 100.0D / snapshot.getTotalCount());
        return snapshot;
    }

    private void count(ProgressSnapshot snapshot, ValidationTask task) {
        if (task.getStatus() == TaskStatus.PENDING) {
            snapshot.setPendingCount(snapshot.getPendingCount() + 1);
        } else if (task.getStatus() == TaskStatus.RUNNING) {
            snapshot.setRunningCount(snapshot.getRunningCount() + 1);
        } else if (task.getStatus() == TaskStatus.PASS) {
            snapshot.setPassCount(snapshot.getPassCount() + 1);
        } else if (task.getStatus() == TaskStatus.FAIL) {
            snapshot.setFailCount(snapshot.getFailCount() + 1);
        } else if (task.getStatus() == TaskStatus.ERROR) {
            snapshot.setErrorCount(snapshot.getErrorCount() + 1);
        } else if (task.getStatus() == TaskStatus.SKIPPED) {
            snapshot.setSkippedCount(snapshot.getSkippedCount() + 1);
        }
    }

    private ValidationBatch getRequiredBatch(String batchId) {
        ValidationBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("未找到核验批次: " + batchId);
        }
        return batch;
    }
}
