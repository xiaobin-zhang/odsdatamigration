package com.example.validator.safety;

public class SqlGuardDecision {
    private final QueryRiskLevel riskLevel;
    private final String reason;
    private final boolean heavyQuery;

    private SqlGuardDecision(QueryRiskLevel riskLevel, String reason, boolean heavyQuery) {
        this.riskLevel = riskLevel;
        this.reason = reason;
        this.heavyQuery = heavyQuery;
    }

    public static SqlGuardDecision allow(String reason, boolean heavyQuery) {
        return new SqlGuardDecision(QueryRiskLevel.ALLOW, reason, heavyQuery);
    }

    public static SqlGuardDecision warn(String reason, boolean heavyQuery) {
        return new SqlGuardDecision(QueryRiskLevel.WARN, reason, heavyQuery);
    }

    public static SqlGuardDecision block(String reason) {
        return new SqlGuardDecision(QueryRiskLevel.BLOCK, reason, true);
    }

    public QueryRiskLevel getRiskLevel() { return riskLevel; }
    public String getReason() { return reason; }
    public boolean isHeavyQuery() { return heavyQuery; }
    public boolean isBlocked() { return riskLevel == QueryRiskLevel.BLOCK; }
}
