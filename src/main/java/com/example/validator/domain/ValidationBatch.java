package com.example.validator.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.validator.common.BatchStatus;
import java.time.LocalDateTime;

/**
 * 核验批次持久化实体。
 *
 * <p>职责：映射 validation_batch 表，保存作业整体状态、当前运行位置和任务统计信息。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@TableName("validation_batch")
public class ValidationBatch {
    @TableId
    private String batchId;
    private BatchStatus status;
    private Integer totalCount;
    private Integer pendingCount;
    private Integer runningCount;
    private Integer passCount;
    private Integer failCount;
    private Integer errorCount;
    private Integer skippedCount;
    private String currentPairName;
    private String currentSourceTable;
    private String currentTargetTable;
    private String currentCheckType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime updateTime;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getPendingCount() { return pendingCount; }
    public void setPendingCount(Integer pendingCount) { this.pendingCount = pendingCount; }
    public Integer getRunningCount() { return runningCount; }
    public void setRunningCount(Integer runningCount) { this.runningCount = runningCount; }
    public Integer getPassCount() { return passCount; }
    public void setPassCount(Integer passCount) { this.passCount = passCount; }
    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public String getCurrentPairName() { return currentPairName; }
    public void setCurrentPairName(String currentPairName) { this.currentPairName = currentPairName; }
    public String getCurrentSourceTable() { return currentSourceTable; }
    public void setCurrentSourceTable(String currentSourceTable) { this.currentSourceTable = currentSourceTable; }
    public String getCurrentTargetTable() { return currentTargetTable; }
    public void setCurrentTargetTable(String currentTargetTable) { this.currentTargetTable = currentTargetTable; }
    public String getCurrentCheckType() { return currentCheckType; }
    public void setCurrentCheckType(String currentCheckType) { this.currentCheckType = currentCheckType; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
