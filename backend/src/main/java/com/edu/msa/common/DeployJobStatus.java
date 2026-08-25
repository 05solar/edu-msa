package com.edu.msa.common;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeployJobStatus {
    QUEUED("queued"), RUNNING("running"), DONE("done"), FAILED("failed");

    private final String code;
    DeployJobStatus(String code) { this.code = code; }

    @JsonValue
    public String code() { return code; }
}
