package com.edu.msa.common;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeploymentStatus {
    PENDING("pending"), VALIDATING("validating"), BUILDING("building"),
    DEPLOYING("deploying"), RUNNING("running"), FAILED("failed");

    private final String code;
    DeploymentStatus(String code) { this.code = code; }

    @JsonValue
    public String code() { return code; }
}
