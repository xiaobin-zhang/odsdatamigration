package com.example.validator.csv;

import com.example.validator.common.CheckType;
import com.example.validator.domain.ShardRange;
import com.example.validator.domain.TableRule;
import com.opencsv.CSVReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * CSV 表规则解析器。
 *
 * <p>职责：读取带表头的 CSV 文件，按表头名称映射为 {@link TableRule}，并执行基础安全校验。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Component
public class CsvRuleParser {
    public static final List<String> REQUIRED_HEADERS = Arrays.asList(
            "pair_name", "enabled", "source_table", "target_table", "primary_key", "checkers",
            "where_clause", "amount_fields", "date_field", "null_fields", "order_fields",
            "compare_fields", "sample_where", "sample_limit", "shard_column", "shard_ranges", "amount_tolerance"
    );

    private final SqlSafetyValidator sqlSafetyValidator;
    private final ResourceLoader resourceLoader;

    /**
     * 创建 CSV 解析器。
     *
     * @param sqlSafetyValidator SQL 安全校验器
     * @param resourceLoader Spring 资源加载器
     */
    public CsvRuleParser(SqlSafetyValidator sqlSafetyValidator, ResourceLoader resourceLoader) {
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 按资源地址解析 CSV。
     *
     * @param location CSV 资源地址，例如 classpath:validation_tables.csv
     * @return 表级核验规则列表
     */
    public List<TableRule> parse(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            InputStream inputStream = resource.getInputStream();
            return parse(inputStream);
        } catch (Exception e) {
            throw new IllegalArgumentException("读取 CSV 配置失败: " + location, e);
        }
    }

    /**
     * 从输入流解析 CSV。
     *
     * @param inputStream CSV 输入流，调用方负责提供 UTF-8 内容
     * @return 表级核验规则列表
     */
    public List<TableRule> parse(InputStream inputStream) {
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new IllegalArgumentException("CSV 必须包含表头");
            }
            Map<String, Integer> headerIndex = buildHeaderIndex(header);
            List<TableRule> rules = new ArrayList<TableRule>();
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (isBlankRow(row)) {
                    continue;
                }
                rules.add(toRule(headerIndex, row));
            }
            return rules;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("解析 CSV 配置失败", e);
        }
    }

    private Map<String, Integer> buildHeaderIndex(String[] header) {
        Map<String, Integer> headerIndex = new LinkedHashMap<String, Integer>();
        Set<String> allowed = new LinkedHashSet<String>(REQUIRED_HEADERS);
        for (int i = 0; i < header.length; i++) {
            String name = header[i].trim();
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("CSV 包含未识别表头: " + name);
            }
            headerIndex.put(name, i);
        }
        for (String required : REQUIRED_HEADERS) {
            if (!headerIndex.containsKey(required)) {
                throw new IllegalArgumentException("CSV 缺少必需表头: " + required);
            }
        }
        return headerIndex;
    }

    private TableRule toRule(Map<String, Integer> headerIndex, String[] row) {
        TableRule rule = new TableRule();
        rule.setPairName(get(row, headerIndex, "pair_name"));
        rule.setEnabled(Boolean.parseBoolean(get(row, headerIndex, "enabled")));
        rule.setSourceTable(get(row, headerIndex, "source_table"));
        rule.setTargetTable(get(row, headerIndex, "target_table"));
        rule.setPrimaryKey(get(row, headerIndex, "primary_key"));
        rule.setWhereClause(defaultCondition(get(row, headerIndex, "where_clause")));
        rule.setSampleWhere(defaultCondition(get(row, headerIndex, "sample_where")));
        rule.setAmountFields(splitList(get(row, headerIndex, "amount_fields")));
        rule.setDateField(get(row, headerIndex, "date_field"));
        rule.setNullFields(splitList(get(row, headerIndex, "null_fields")));
        rule.setOrderFields(splitList(get(row, headerIndex, "order_fields")));
        rule.setCompareFields(splitList(get(row, headerIndex, "compare_fields")));
        rule.setSampleLimit(parseInt(get(row, headerIndex, "sample_limit"), 1000));
        rule.setShardColumn(get(row, headerIndex, "shard_column"));
        rule.setShardRanges(parseShardRanges(get(row, headerIndex, "shard_ranges")));
        rule.setAmountTolerance(parseDecimal(get(row, headerIndex, "amount_tolerance"), new BigDecimal("0.00")));
        rule.setCheckers(parseCheckers(get(row, headerIndex, "checkers")));
        validateRule(rule);
        return rule;
    }

    private void validateRule(TableRule rule) {
        if (!rule.isEnabled()) {
            return;
        }
        // CSV 按表头映射后马上做字段和条件校验，启动阶段就能暴露配置错误，避免漏检或执行危险 SQL。
        sqlSafetyValidator.assertIdentifier(rule.getSourceTable(), "source_table");
        sqlSafetyValidator.assertIdentifier(rule.getTargetTable(), "target_table");
        if (StringUtils.hasText(rule.getPrimaryKey())) {
            sqlSafetyValidator.assertIdentifier(rule.getPrimaryKey(), "primary_key");
        }
        sqlSafetyValidator.assertIdentifierList(rule.getAmountFields(), "amount_fields");
        if (StringUtils.hasText(rule.getDateField())) {
            sqlSafetyValidator.assertIdentifier(rule.getDateField(), "date_field");
        }
        sqlSafetyValidator.assertIdentifierList(rule.getNullFields(), "null_fields");
        sqlSafetyValidator.assertIdentifierList(rule.getOrderFields(), "order_fields");
        sqlSafetyValidator.assertIdentifierList(rule.getCompareFields(), "compare_fields");
        if (StringUtils.hasText(rule.getShardColumn())) {
            sqlSafetyValidator.assertIdentifier(rule.getShardColumn(), "shard_column");
        }
        sqlSafetyValidator.assertSafeCondition(rule.getWhereClause(), "where_clause");
        sqlSafetyValidator.assertSafeCondition(rule.getSampleWhere(), "sample_where");
    }

    private List<CheckType> parseCheckers(String raw) {
        List<CheckType> result = new ArrayList<CheckType>();
        for (String item : splitList(raw)) {
            try {
                result.add(CheckType.valueOf(item.trim()));
            } catch (Exception e) {
                throw new IllegalArgumentException("非法 Checker 名称: " + item);
            }
        }
        return result;
    }

    private List<ShardRange> parseShardRanges(String raw) {
        List<ShardRange> ranges = new ArrayList<ShardRange>();
        if (!StringUtils.hasText(raw)) {
            return ranges;
        }
        for (String part : raw.split(";")) {
            String[] pair = part.trim().split("-");
            if (pair.length != 2) {
                throw new IllegalArgumentException("非法分片范围: " + part);
            }
            ranges.add(new ShardRange(pair[0].trim(), pair[1].trim()));
        }
        return ranges;
    }

    private List<String> splitList(String raw) {
        List<String> values = new ArrayList<String>();
        if (!StringUtils.hasText(raw)) {
            return values;
        }
        for (String item : raw.split(",")) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private String defaultCondition(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : "1=1";
    }

    private int parseInt(String raw, int defaultValue) {
        return StringUtils.hasText(raw) ? Integer.parseInt(raw.trim()) : defaultValue;
    }

    private BigDecimal parseDecimal(String raw, BigDecimal defaultValue) {
        return StringUtils.hasText(raw) ? new BigDecimal(raw.trim()) : defaultValue;
    }

    private String get(String[] row, Map<String, Integer> headerIndex, String name) {
        Integer index = headerIndex.get(name);
        return index != null && index < row.length ? row[index].trim() : "";
    }

    private boolean isBlankRow(String[] row) {
        for (String cell : row) {
            if (StringUtils.hasText(cell)) {
                return false;
            }
        }
        return true;
    }
}
