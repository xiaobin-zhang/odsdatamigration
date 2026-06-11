package com.example.validator.checker;

import com.example.validator.common.CheckType;
import com.example.validator.config.ValidatorProperties;
import com.example.validator.domain.CheckResult;
import com.example.validator.domain.QueryResult;
import com.example.validator.domain.TableRule;
import com.example.validator.domain.ValidationTask;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * MD5 抽样核验器。
 *
 * <p>职责：按主键排序抽样，标准化关键字段后计算 MD5 摘要，并比较源端与目标端摘要序列。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
@Component
public class Md5SampleChecker extends AbstractValidationChecker {
    /**
     * 获取当前 Checker 类型。
     *
     * @return MD5_SAMPLE
     */
    public CheckType getType() { return CheckType.MD5_SAMPLE; }

    /**
     * 判断表规则是否具备 MD5 抽样核验所需配置。
     *
     * @param tableRule 表级核验规则
     * @return true 表示主键和比对字段均已配置
     */
    public boolean support(TableRule tableRule) {
        return !tableRule.getPrimaryKeys().isEmpty() && !tableRule.getCompareFields().isEmpty();
    }

    /**
     * 生成 MD5 抽样核验任务。
     *
     * @param pair 数据库源端与目标端配对
     * @param tableRule 表级核验规则
     * @return MD5 抽样核验任务列表
     */
    public List<ValidationTask> plan(ValidatorProperties.ComparePair pair, final TableRule tableRule) {
        return buildTasks(pair, tableRule, (datasourceName, table, shardRange) ->
                "select " + SqlBuilder.columns(columns(tableRule))
                        + " " + SqlBuilder.fromWithShard(table, tableRule.getSampleWhere(), tableRule, shardRange)
                        + " order by " + SqlBuilder.columns(tableRule.getPrimaryKeys())
                        + " limit " + tableRule.getSampleLimit());
    }

    /**
     * 比较源端与目标端样本行的 MD5 摘要序列。
     *
     * @param sourceResult 源端抽样查询结果
     * @param targetResult 目标端抽样查询结果
     * @param tableRule 表级核验规则
     * @return 摘要序列一致返回 PASS，否则返回 FAIL
     */
    public CheckResult compare(QueryResult sourceResult, QueryResult targetResult, TableRule tableRule) {
        List<String> sourceHashes = hashes(sourceResult, tableRule);
        List<String> targetHashes = hashes(targetResult, tableRule);
        return sourceHashes.equals(targetHashes)
                ? CheckResult.pass("MD5 抽样一致")
                : CheckResult.fail("MD5 抽样不一致, source=" + sourceHashes + ", target=" + targetHashes);
    }

    private List<String> hashes(QueryResult result, TableRule tableRule) {
        List<String> hashes = new ArrayList<String>();
        for (Map<String, Object> row : result.getRows()) {
            StringBuilder payload = new StringBuilder();
            for (String field : tableRule.getCompareFields()) {
                if (payload.length() > 0) {
                    payload.append('|');
                }
                Object value = row.get(field);
                if (value == null) {
                    value = row.get(field.toUpperCase());
                }
                // MD5 前统一做字段标准化：NULL 使用固定占位符，其他值 trim 后参与摘要。
                // 这样可以减少数据库驱动返回类型差异造成的误报，同时保留字符集/截断类问题的识别能力。
                payload.append(value == null ? "<NULL>" : String.valueOf(value).trim());
            }
            hashes.add(md5(payload.toString()));
        }
        return hashes;
    }

    private List<String> columns(TableRule tableRule) {
        List<String> columns = new ArrayList<String>();
        columns.addAll(tableRule.getPrimaryKeys());
        for (String field : tableRule.getCompareFields()) {
            if (!columns.contains(field)) {
                columns.add(field);
            }
        }
        return columns;
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return Hex.encodeHexString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算 MD5 失败", e);
        }
    }
}
