package com.example.validator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.validator.common.CheckType;
import com.example.validator.common.TaskStatus;

/**
 * 核验任务持久化实体。
 *
 * <p>职责：映射 validation_task 表，保存每个表/分片/Checker 任务的 SQL、状态和执行结果。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@TableName("validation_task")
public class ValidationTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String pairName;
    private String sourceName;
    private String targetName;
    private String sourceTable;
    private String targetTable;
    private CheckType checkType;
    private Integer shardNo;
    private TaskStatus status = TaskStatus.PENDING;
    private Integer retryCount = 0;
    private String sourceSql;
    private String targetSql;
    private String errorMessage;
    private String resultSummary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getPairName() { return pairName; }
    public void setPairName(String pairName) { this.pairName = pairName; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public CheckType getCheckType() { return checkType; }
    public void setCheckType(CheckType checkType) { this.checkType = checkType; }
    public Integer getShardNo() { return shardNo; }
    public void setShardNo(Integer shardNo) { this.shardNo = shardNo; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getSourceSql() { return sourceSql; }
    public void setSourceSql(String sourceSql) { this.sourceSql = sourceSql; }
    public String getTargetSql() { return targetSql; }
    public void setTargetSql(String targetSql) { this.targetSql = targetSql; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
}
