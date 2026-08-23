package com.securetrade.accessapi.decision;

// Reason codes for trade decisions
public final class ReasonCode {

    public static final String EXEC_PASS_STANDARD = "EXEC_PASS_STANDARD";
    public static final String ERR_AGENT_SUSPENDED = "ERR_AGENT_SUSPENDED";
    public static final String ERR_EXCEEDS_AGENT_LIMIT = "ERR_EXCEEDS_AGENT_LIMIT";
    public static final String ERR_EXCEEDS_HARD_LIMIT = "ERR_EXCEEDS_HARD_LIMIT";
    public static final String FLAG_HIGH_VOL_RISK = "FLAG_HIGH_VOL_RISK";

    private ReasonCode() {
    }
}
