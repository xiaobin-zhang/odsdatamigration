package com.example.validator.csv;

import com.example.validator.common.CheckType;
import com.example.validator.common.ShardType;
import com.example.validator.domain.ShardRange;
import com.example.validator.domain.TableRule;
import com.opencsv.CSVReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * CSV 表规则解析器。
 *
 * <p>读取带表头的 CSV 文件，按表头名称映射为 {@link TableRule}，并执行基础安全校验。</p>
 */
@Component
public class CsvRuleParser {
    private static final int MAX_SAMPLE_LIMIT = 10000;
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern POSITIVE_INTEGER = Pattern.compile("\\d+");
    private static final Pattern INTERVAL_STEP = Pattern.compile("\\d+[smhd]");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final List<String> REQUIRED_HEADERS = Arrays.asList(
            "pair_name", "enabled", "source_table", "target_table", "primary_key", "checkers",
            "where_clause", "amount_fields", "date_field", "null_fields", "order_fields",
            "compare_fields", "sample_where", "sample_limit", "shard_column", "shard_type", "shard_ranges", "amount_tolerance"
    );

    private final SqlSafetyValidator sqlSafetyValidator;
    private final ResourceLoader resourceLoader;

    public CsvRuleParser(SqlSafetyValidator sqlSafetyValidator, ResourceLoader resourceLoader) {
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.resourceLoader = resourceLoader;
    }

    public List<TableRule> parse(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            InputStream inputStream = resource.getInputStream();
            return parse(inputStream);
        } catch (Exception e) {
            throw new IllegalArgumentException("读取 CSV 配置失败: " + location, e);
        }
    }

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
        rule.setPrimaryKeys(splitList(get(row, headerIndex, "primary_key")));
        rule.setWhereClause(defaultCondition(get(row, headerIndex, "where_clause")));
        rule.setSampleWhere(defaultCondition(get(row, headerIndex, "sample_where")));
        rule.setAmountFields(splitList(get(row, headerIndex, "amount_fields")));
        rule.setDateField(get(row, headerIndex, "date_field"));
        rule.setNullFields(splitList(get(row, headerIndex, "null_fields")));
        rule.setOrderFields(splitList(get(row, headerIndex, "order_fields")));
        rule.setCompareFields(splitList(get(row, headerIndex, "compare_fields")));
        rule.setSampleLimit(parseInt(get(row, headerIndex, "sample_limit"), 1000));
        rule.setShardColumn(get(row, headerIndex, "shard_column"));
        rule.setShardType(parseShardType(get(row, headerIndex, "shard_type")));
        rule.setShardRanges(parseShardRanges(get(row, headerIndex, "shard_ranges"), rule.getShardType()));
        rule.setAmountTolerance(parseDecimal(get(row, headerIndex, "amount_tolerance"), new BigDecimal("0.00")));
        rule.setCheckers(parseCheckers(get(row, headerIndex, "checkers")));
        validateRule(rule);
        return rule;
    }

    private void validateRule(TableRule rule) {
        if (!rule.isEnabled()) {
            return;
        }
        sqlSafetyValidator.assertIdentifier(rule.getSourceTable(), "source_table");
        sqlSafetyValidator.assertIdentifier(rule.getTargetTable(), "target_table");
        sqlSafetyValidator.assertIdentifierList(rule.getPrimaryKeys(), "primary_key");
        sqlSafetyValidator.assertIdentifierList(rule.getAmountFields(), "amount_fields");
        if (StringUtils.hasText(rule.getDateField())) {
            sqlSafetyValidator.assertIdentifier(rule.getDateField(), "date_field");
        }
        sqlSafetyValidator.assertIdentifierList(rule.getNullFields(), "null_fields");
        sqlSafetyValidator.assertIdentifierList(rule.getOrderFields(), "order_fields");
        sqlSafetyValidator.assertIdentifierList(rule.getCompareFields(), "compare_fields");
        validateShardConfig(rule);
        sqlSafetyValidator.assertSafeCondition(rule.getWhereClause(), "where_clause");
        sqlSafetyValidator.assertSafeCondition(rule.getSampleWhere(), "sample_where");
    }

    private void validateShardConfig(TableRule rule) {
        boolean hasShardColumn = StringUtils.hasText(rule.getShardColumn());
        boolean hasShardType = rule.getShardType() != null;
        boolean hasShardRanges = !rule.getShardRanges().isEmpty();
        if (!hasShardColumn && !hasShardType && !hasShardRanges) {
            return;
        }
        if (!hasShardColumn || !hasShardType || !hasShardRanges) {
            throw new IllegalArgumentException("启用分片时 shard_column、shard_type 和 shard_ranges 必须同时配置");
        }
        if (rule.getShardType() == ShardType.OFFSET && rule.getPrimaryKeys().isEmpty()) {
            throw new IllegalArgumentException("OFFSET 分片必须配置 primary_key 作为稳定排序字段");
        }
        sqlSafetyValidator.assertIdentifier(rule.getShardColumn(), "shard_column");
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

    private ShardType parseShardType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return ShardType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 shard_type: " + raw);
        }
    }

    private List<ShardRange> parseShardRanges(String raw, ShardType shardType) {
        List<ShardRange> ranges = new ArrayList<ShardRange>();
        if (!StringUtils.hasText(raw)) {
            return ranges;
        }
        if (shardType == null) {
            throw new IllegalArgumentException("配置 shard_ranges 时必须配置 shard_type");
        }
        if (shardType == ShardType.NUMBER_MOD) {
            int shardCount = parsePositiveShardCount(raw, "NUMBER_MOD");
            for (int i = 0; i < shardCount; i++) {
                ranges.add(ShardRange.mod(shardCount, i));
            }
            return ranges;
        }
        if (shardType == ShardType.OFFSET) {
            ranges.add(ShardRange.offsetPlan(parsePositiveShardCount(raw, "OFFSET")));
            return ranges;
        }
        if (isIntervalType(shardType)) {
            return parseIntervalRanges(raw, shardType);
        }
        for (String part : raw.split(";")) {
            String[] pair = splitShardRange(part, shardType);
            String from = pair[0].trim();
            String to = pair[1].trim();
            validateShardValue(from, shardType, part);
            validateShardValue(to, shardType, part);
            ranges.add(new ShardRange(from, to));
        }
        return ranges;
    }

    private int parsePositiveShardCount(String raw, String shardType) {
        String trimmed = raw.trim();
        if (!POSITIVE_INTEGER.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(shardType + " shard_ranges 必须是正整数: " + raw);
        }
        int value = Integer.parseInt(trimmed);
        if (value <= 0) {
            throw new IllegalArgumentException(shardType + " shard_ranges 必须大于 0: " + raw);
        }
        return value;
    }

    private boolean isIntervalType(ShardType shardType) {
        return shardType == ShardType.DATE_INTERVAL
                || shardType == ShardType.TIME_INTERVAL
                || shardType == ShardType.DATETIME_INTERVAL;
    }

    private List<ShardRange> parseIntervalRanges(String raw, ShardType shardType) {
        String[] parts = raw.trim().split("~", -1);
        if (parts.length != 3 || !StringUtils.hasText(parts[0])
                || !StringUtils.hasText(parts[1]) || !StringUtils.hasText(parts[2])) {
            throw new IllegalArgumentException("间隔分片必须使用 start~end~step: " + raw);
        }
        String start = parts[0].trim();
        String end = parts[1].trim();
        String step = parts[2].trim().toLowerCase();
        if (!INTERVAL_STEP.matcher(step).matches()) {
            throw new IllegalArgumentException("非法间隔步长: " + raw);
        }
        int amount = Integer.parseInt(step.substring(0, step.length() - 1));
        if (amount <= 0) {
            throw new IllegalArgumentException("间隔步长必须大于 0: " + raw);
        }
        ChronoUnit unit = intervalUnit(step.charAt(step.length() - 1), shardType, raw);
        if (shardType == ShardType.DATE_INTERVAL) {
            return parseDateIntervals(start, end, amount, unit, raw);
        }
        if (shardType == ShardType.TIME_INTERVAL) {
            return parseTimeIntervals(start, end, amount, unit, raw);
        }
        return parseDateTimeIntervals(start, end, amount, unit, raw);
    }

    private ChronoUnit intervalUnit(char unit, ShardType shardType, String raw) {
        if (unit == 'd') {
            return ChronoUnit.DAYS;
        }
        if (shardType == ShardType.DATE_INTERVAL) {
            throw new IllegalArgumentException("DATE_INTERVAL 仅支持 d 步长: " + raw);
        }
        if (unit == 'h') {
            return ChronoUnit.HOURS;
        }
        if (unit == 'm') {
            return ChronoUnit.MINUTES;
        }
        if (unit == 's') {
            return ChronoUnit.SECONDS;
        }
        throw new IllegalArgumentException("非法间隔步长: " + raw);
    }

    private List<ShardRange> parseDateIntervals(String start, String end, int amount, ChronoUnit unit, String raw) {
        try {
            LocalDate current = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            if (!current.isBefore(endDate)) {
                throw new IllegalArgumentException("间隔分片 end 必须大于 start: " + raw);
            }
            List<ShardRange> ranges = new ArrayList<ShardRange>();
            while (current.isBefore(endDate)) {
                LocalDate next = current.plus(amount, unit);
                if (next.isAfter(endDate)) {
                    next = endDate;
                }
                ranges.add(ShardRange.interval(current.toString(), next.toString()));
                current = next;
            }
            return ranges;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("非法间隔分片: " + raw, e);
        }
    }

    private List<ShardRange> parseTimeIntervals(String start, String end, int amount, ChronoUnit unit, String raw) {
        try {
            LocalTime current = LocalTime.parse(start);
            LocalTime endTime = LocalTime.parse(end);
            if (!current.isBefore(endTime)) {
                throw new IllegalArgumentException("间隔分片 end 必须大于 start: " + raw);
            }
            List<ShardRange> ranges = new ArrayList<ShardRange>();
            while (current.isBefore(endTime)) {
                LocalTime next = current.plus(amount, unit);
                if (!next.isAfter(current) || next.isAfter(endTime)) {
                    next = endTime;
                }
                ranges.add(ShardRange.interval(current.format(TIME_FORMATTER), next.format(TIME_FORMATTER)));
                current = next;
            }
            return ranges;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("非法间隔分片: " + raw, e);
        }
    }

    private List<ShardRange> parseDateTimeIntervals(String start, String end, int amount, ChronoUnit unit, String raw) {
        try {
            LocalDateTime current = LocalDateTime.parse(start, DATE_TIME_FORMATTER);
            LocalDateTime endDateTime = LocalDateTime.parse(end, DATE_TIME_FORMATTER);
            if (!current.isBefore(endDateTime)) {
                throw new IllegalArgumentException("间隔分片 end 必须大于 start: " + raw);
            }
            List<ShardRange> ranges = new ArrayList<ShardRange>();
            while (current.isBefore(endDateTime)) {
                LocalDateTime next = current.plus(amount, unit);
                if (next.isAfter(endDateTime)) {
                    next = endDateTime;
                }
                ranges.add(ShardRange.interval(current.format(DATE_TIME_FORMATTER), next.format(DATE_TIME_FORMATTER)));
                current = next;
            }
            return ranges;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("非法间隔分片: " + raw, e);
        }
    }

    private String[] splitShardRange(String part, ShardType shardType) {
        String trimmed = part.trim();
        if (trimmed.contains("~")) {
            String[] pair = trimmed.split("~", -1);
            if (pair.length == 2) {
                return pair;
            }
        } else if (shardType == ShardType.NUMBER) {
            String[] pair = trimmed.split("-", -1);
            if (pair.length == 2) {
                return pair;
            }
        }
        throw new IllegalArgumentException("非法分片范围: " + part);
    }

    private void validateShardValue(String value, ShardType shardType, String part) {
        try {
            if (shardType == ShardType.NUMBER && NUMERIC_LITERAL.matcher(value).matches()) {
                return;
            }
            if (shardType == ShardType.DATE) {
                LocalDate.parse(value);
                return;
            }
            if (shardType == ShardType.TIME) {
                LocalTime.parse(value);
                return;
            }
            if (shardType == ShardType.DATETIME) {
                LocalDateTime.parse(value, DATE_TIME_FORMATTER);
                return;
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("非法分片范围: " + part, e);
        }
        throw new IllegalArgumentException("非法分片范围: " + part);
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
        int value = StringUtils.hasText(raw) ? Integer.parseInt(raw.trim()) : defaultValue;
        // sample_limit 会直接进入 LIMIT 子句，限制范围可以避免配置错误触发超大抽样查询。
        if (value <= 0 || value > MAX_SAMPLE_LIMIT) {
            throw new IllegalArgumentException("sample_limit 必须在 1 到 " + MAX_SAMPLE_LIMIT + " 之间: " + value);
        }
        return value;
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
