package com.example.validator.safety;

public class SafetyViolationException extends RuntimeException {
    private final SqlGuardDecision decision;

    public SafetyViolationException(SqlGuardDecision decision) {
        super(decision.getReason());
        this.decision = decision;
    }

    public SqlGuardDecision getDecision() {
        return decision;
    }
}
