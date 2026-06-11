package com.example.validator;

import com.example.validator.common.CheckType;
import com.example.validator.common.ShardType;
import com.example.validator.csv.CsvRuleParser;
import com.example.validator.csv.SqlSafetyValidator;
import com.example.validator.domain.ShardRange;
import com.example.validator.domain.TableRule;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.*;

class CsvRuleParserTest {
    private final CsvRuleParser parser = new CsvRuleParser(new SqlSafetyValidator(), new DefaultResourceLoader());

    @Test
    void parseHeaderByNameAllowsColumnReorderAndCompositePrimaryKey() {
        String csv = "enabled,pair_name,target_table,source_table,primary_key,checkers,where_clause,amount_fields,date_field,null_fields,order_fields,compare_fields,sample_where,sample_limit,shard_column,shard_type,shard_ranges,amount_tolerance\n"
                + "true,db1_compare,t_order,t_order,\"tenant_id,order_id\",\"ROW_COUNT,MD5_SAMPLE\",1=1,,create_time,,order_id,\"order_id,status\",1=1,10,,,,0.00\n";
        List<TableRule> rules = parser.parse(stream(csv));

        assertEquals(1, rules.size());
        assertEquals("db1_compare", rules.get(0).getPairName());
        assertEquals(2, rules.get(0).getPrimaryKeys().size());
        assertEquals("tenant_id", rules.get(0).getPrimaryKeys().get(0));
        assertEquals("order_id", rules.get(0).getPrimaryKeys().get(1));
        assertNull(rules.get(0).getShardType());
        assertTrue(rules.get(0).getCheckers().contains(CheckType.ROW_COUNT));
        assertTrue(rules.get(0).getCheckers().contains(CheckType.MD5_SAMPLE));
    }

    @Test
    void missingRequiredHeaderFailsFast() {
        String csv = "pair_name,enabled,source_table\n"
                + "db1_compare,true,t_order\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void unknownHeaderFailsFast() {
        String header = String.join(",", CsvRuleParser.REQUIRED_HEADERS) + ",unknown\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(header)));
    }

    @Test
    void disabledAndEmptyCheckersAreParsed() throws Exception {
        List<TableRule> rules = parser.parse(new DefaultResourceLoader().getResource("classpath:validation_tables_test.csv").getInputStream());
        assertTrue(rules.stream().anyMatch(rule -> !rule.isEnabled()));
        assertTrue(rules.stream().anyMatch(rule -> rule.isEnabled() && rule.getCheckers().isEmpty()));
    }

    @Test
    void invalidCheckerNameFailsFast() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,NO_SUCH_CHECKER,1=1,,,,,,1=1,10,,,,0.00\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void dangerousWhereClauseFailsFast() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,\"1=1; drop table t\",,,,,,1=1,10,,,,0.00\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void invalidSampleLimitFailsFast() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10001,,,,0.00\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void shardTypeMayBeBlankWhenShardConfigIsBlank() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,,,,0.00\n";
        List<TableRule> rules = parser.parse(stream(csv));

        assertNull(rules.get(0).getShardType());
        assertTrue(rules.get(0).getShardRanges().isEmpty());
    }

    @Test
    void shardConfigRequiresShardType() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,id,,\"1~10\",0.00\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void shardTypeAloneFailsFast() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,,NUMBER,,0.00\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void offsetShardRequiresPrimaryKeyForStableOrdering() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,,ROW_COUNT,1=1,,,,,,1=1,10,status,OFFSET,\"2\",0.00\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void numberShardRangeSupportsLegacyDashAndTilde() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,id,NUMBER,\"1-10;11~20\",0.00\n";
        List<TableRule> rules = parser.parse(stream(csv));

        assertEquals(ShardType.NUMBER, rules.get(0).getShardType());
        assertEquals(2, rules.get(0).getShardRanges().size());
    }

    @Test
    void dateTimeShardRangesAreParsed() {
        assertShardRangeAccepted(ShardType.DATE, "2026-01-01~2026-01-31");
        assertShardRangeAccepted(ShardType.TIME, "00:00:00~23:59:59");
        assertShardRangeAccepted(ShardType.DATETIME, "2026-01-01 00:00:00~2026-01-31 23:59:59");
    }

    @Test
    void unsafeShardRangeFailsFast() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,id,NUMBER,\"1-10;11-20 or 1=1\",0.00\n";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void numberModShardRangeExpandsToRemainders() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,id,NUMBER_MOD,\"4\",0.00\n";
        List<TableRule> rules = parser.parse(stream(csv));

        assertEquals(ShardType.NUMBER_MOD, rules.get(0).getShardType());
        assertEquals(4, rules.get(0).getShardRanges().size());
        assertEquals(ShardRange.Strategy.MOD, rules.get(0).getShardRanges().get(0).getStrategy());
        assertEquals(4, rules.get(0).getShardRanges().get(0).getModulus());
        assertEquals(3, rules.get(0).getShardRanges().get(3).getRemainder());
    }

    @Test
    void intervalShardRangeExpandsToHalfOpenSlices() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,create_time,DATE_INTERVAL,\"2026-01-01~2026-01-04~1d\",0.00\n";
        List<TableRule> rules = parser.parse(stream(csv));

        assertEquals(3, rules.get(0).getShardRanges().size());
        assertEquals(ShardRange.Strategy.INTERVAL, rules.get(0).getShardRanges().get(0).getStrategy());
        assertEquals("2026-01-01", rules.get(0).getShardRanges().get(0).getFrom());
        assertEquals("2026-01-02", rules.get(0).getShardRanges().get(0).getTo());
    }

    @Test
    void offsetShardRangeKeepsRequestedShardCountForPlanning() {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,id,OFFSET,\"5\",0.00\n";
        List<TableRule> rules = parser.parse(stream(csv));

        assertEquals(1, rules.get(0).getShardRanges().size());
        assertEquals(ShardRange.Strategy.OFFSET, rules.get(0).getShardRanges().get(0).getStrategy());
        assertEquals(5, rules.get(0).getShardRanges().get(0).getShardCount());
    }

    @Test
    void invalidStrategyShardRangesFailFast() {
        String modCsv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,id,NUMBER_MOD,\"0\",0.00\n";
        String intervalCsv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,create_time,DATE_INTERVAL,\"2026-01-01~2026-01-02~1h\",0.00\n";
        String zeroIntervalCsv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,create_time,DATE_INTERVAL,\"2026-01-01~2026-01-02~0d\",0.00\n";
        String offsetCsv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,id,OFFSET,\"abc\",0.00\n";

        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(modCsv)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(intervalCsv)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(zeroIntervalCsv)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(stream(offsetCsv)));
    }

    private void assertShardRangeAccepted(ShardType shardType, String shardRanges) {
        String csv = header()
                + "db1_compare,true,t_order,t_order,id,ROW_COUNT,1=1,,,,,,1=1,10,create_time,"
                + shardType.name() + ",\"" + shardRanges + "\",0.00\n";
        List<TableRule> rules = parser.parse(stream(csv));

        assertEquals(shardType, rules.get(0).getShardType());
        assertEquals(1, rules.get(0).getShardRanges().size());
    }

    private String header() {
        return String.join(",", CsvRuleParser.REQUIRED_HEADERS) + "\n";
    }

    private ByteArrayInputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
