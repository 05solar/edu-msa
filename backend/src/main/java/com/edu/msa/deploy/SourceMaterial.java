package com.edu.msa.deploy;

/** 레포에서 수집한 배포 재료. workDir 은 git 수집 시의 로컬 경로(예제는 null). */
public record SourceMaterial(String serviceYaml, boolean hasDockerfile, String resolvedFrom, String workDir) {}
