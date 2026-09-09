package com.edu.msa.deploy;

/**
 * 레포에서 수집한 배포 재료. workDir 은 git 수집 시의 로컬 경로(예제는 null).
 * ephemeralWorkDir 이 true 면 git clone 으로 만든 임시 디렉터리라는 뜻이며,
 * 사용이 끝난 뒤 호출부(deploy/validate)가 삭제할 책임을 진다.
 * local:// 경로(플랫폼 동봉 예제)는 false 라 절대 삭제되지 않는다.
 */
public record SourceMaterial(String serviceYaml, boolean hasDockerfile, String resolvedFrom,
                             String workDir, boolean ephemeralWorkDir) {

    public SourceMaterial(String serviceYaml, boolean hasDockerfile, String resolvedFrom, String workDir) {
        this(serviceYaml, hasDockerfile, resolvedFrom, workDir, false);
    }
}
