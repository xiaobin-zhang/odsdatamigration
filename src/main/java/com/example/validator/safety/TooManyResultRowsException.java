package com.example.validator.safety;

public class TooManyResultRowsException extends SafetyViolationException {
    public TooManyResultRowsException(int maxRows) {
        super(SqlGuardDecision.block("SQL result rows exceed safety limit: maxResultRows=" + maxRows));
    }
}
