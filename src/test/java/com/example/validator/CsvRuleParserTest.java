package com.example.validator;

import com.example.validator.common.CheckType;
import com.example.validator.csv.CsvRuleParser;
import com.example.validator.csv.SqlSafetyValidator;
import com.example.validator.domain.TableRule;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CSV 表规则解析器测试。
 *
 * <p>职责：验证 CSV 表头、列名映射、禁用表、空 Checker 和危险配置拦截能力。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
class CsvRuleParserTest {
    private final CsvRuleParser parser = new CsvRuleParser(new SqlSafetyValidator(), new DefaultResourceLoader());

    /**
     * 验证 CSV 可以按表头名称解析，列顺序变化不影响字段映射。
     */
    @Test
    void parseHeaderByNameAllowsColumnReorder() {
        String csv = "enabled,pair_name,target_table,source_table,primary_key,checkers,where_clause,amount_fields,date_field,null_fields,order_fields,compare_fields,sample_where,sample_limit,shard_column,shard_ranges,amount_tolerance\n"
                + "true,db1_compare,t_order,t_order,order_id,\"ROW_COUNT,MD5_SAMPLE\",1=1,,create_time,,order_id,\"order_id,status\",1=1,10,,,\n";
        List<TableRule> rules = parser.parse(stream(csv));
        assertEquals(1, rules.size());
        assertEquals("db1_compare", rules.get(0).getPairName());
        assertEquals(2, rules.get(0).getCheckers().size());
        assertTrue(rules.get(0).getCheckers().contains(CheckType.ROW_COUNT));
        assertTrue(rules.get(0).getCheckers().contains(CheckType.MD5_SAMPLE));
    }

    /**
     * 验证缺少必需表头时解析失败。
     */
    @Test
    void missingRequiredHeaderFailsFast() {
        String csv = "pair_name,enabled,source_table\n"
                + "db1_compare,true,t_order\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    /**
     * 验证出现未知表头时解析失败。
     */
    @Test
    void unknownHeaderFailsFast() {
        String header = String.join(",", CsvRuleParser.REQUIRED_HEADERS) + ",unknown\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(header)));
    }

    /**
     * 验证禁用表和空 Checker 表可以被正确解析。
     *
     * @throws Exception 读取测试资源失败时抛出
     */
    @Test
    void disabledAndEmptyCheckersAreParsed() throws Exception {
        List<TableRule> rules = parser.parse(new DefaultResourceLoader().getResource("classpath:validation_tables_test.csv").getInputStream());
        assertTrue(rules.stream().anyMatch(rule -> !rule.isEnabled()));
        assertTrue(rules.stream().anyMatch(rule -> rule.isEnabled() && rule.getCheckers().isEmpty()));
    }

    /**
     * 验证非法 Checker 名称会被快速拦截。
     */
    @Test
    void invalidCheckerNameFailsFast() {
        String csv = String.join(",", CsvRuleParser.REQUIRED_HEADERS) + "\n"
                + "db1_compare,true,t_order,t_order,id,NO_SUCH_CHECKER,1=1,,,,,,1=1,10,,,\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    /**
     * 验证危险 where 条件会被快速拦截。
     */
    @Test
    void dangerousWhereClauseFailsFast() {
        String csv = String.join(",", CsvRuleParser.REQUIRED_HEADERS) + "\n"
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,\"1=1; drop table t\",,,,,,1=1,10,,,\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    private ByteArrayInputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
