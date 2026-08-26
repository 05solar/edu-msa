package com.edu.msa.deploy.domain;

import java.util.List;

/** service.yaml 파싱 결과 (MSA_SERVICE_SPEC.md). */
public record ServiceSpec(
        String name,
        String slug,
        String category,
        List<String> purposes,
        List<String> tech,
        String summary,
        int port,
        String health,
        String cpu,
        String memory,
        int gpu
) {
    public String healthOrDefault() {
        return (health != null && !health.isBlank()) ? health : "/healthz";
    }
    public String cpuOrDefault() {
        return (cpu != null && !cpu.isBlank()) ? cpu : "250m";
    }
    public String memoryOrDefault() {
        return (memory != null && !memory.isBlank()) ? memory : "256Mi";
    }
    /** GPU 요청 개수(nvidia.com/gpu). 0이면 GPU 미사용(대부분의 서비스). */
    public boolean usesGpu() {
        return gpu > 0;
    }
}
