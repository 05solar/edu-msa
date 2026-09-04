package com.edu.msa.deploy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 배포 파이프라인 설정 (edu.deploy.*). */
@Component
public class DeployProperties {
    @Value("${edu.deploy.mode:simulate}")      private String mode;          // simulate | docker | real
    @Value("${edu.deploy.namespace:edu-services}") private String namespace;             // 내부(반신뢰)
    @Value("${edu.deploy.namespace-public:edu-services-public}") private String namespacePublic; // 외부(비신뢰)
    @Value("${edu.deploy.ingress-host:edu.internal}") private String ingressHost;
    @Value("${edu.deploy.registry:registry.edu.internal}") private String registry;
    @Value("${edu.deploy.build-namespace:edu-platform}") private String buildNamespace; // Kaniko 빌드 실행 ns
    // 레지스트리가 HTTP(비TLS)일 때 Kaniko 에 --insecure 를 준다 (kind 로컬 레지스트리, 일부 사내 레지스트리)
    @Value("${edu.deploy.kaniko-insecure:false}") private boolean kanikoInsecure;
    @Value("${edu.deploy.replicas:1}")         private int replicas;
    @Value("${edu.deploy.cpu-limit:500m}")     private String cpuLimit;
    @Value("${edu.deploy.memory-limit:512Mi}") private String memoryLimit;
    // 로컬 Docker 실배포 모드용
    @Value("${edu.deploy.host-port-base:31000}") private int hostPortBase;
    @Value("${edu.deploy.app-host:localhost}")   private String appHost;
    @Value("${edu.deploy.auto-on-approve:true}") private boolean autoOnApprove;
    // 서브도메인 리버스 프록시(Traefik) 배포용
    @Value("${edu.deploy.subdomain-base:localhost}") private String subdomainBase;   // url = http://<slug>.<base>
    @Value("${edu.deploy.proxy-network:eduproxy}")   private String proxyNetwork;    // 배포 컨테이너가 합류할 네트워크
    @Value("${edu.deploy.dynamic-dir:}")             private String dynamicDir;      // Traefik 파일 프로바이더 라우트 경로
    // 내부 Gitea 연동(3단계): 비공개 레포 clone 자격 증명. 호스트 미설정 시 기존 동작(자격 증명 미주입).
    @Value("${edu.deploy.gitea-host:}")                 private String giteaHost;    // 예: gitea.edu.internal
    @Value("${edu.deploy.gitea-user:edu-deploy-bot}")   private String giteaUser;
    @Value("${edu.deploy.gitea-token:}")                private String giteaToken;
    // clone 시 실제 접근 주소(내부용). 사용자는 공개 주소(gitea-host)로 등록하고, 수집기는 이 주소로 받는다.
    // 예: 인클러스터 http://gitea-http.gitea.svc:3000 · 로컬 개발 http://host.docker.internal:3000
    // 비워 두면 등록 주소 그대로 사용. (git/curl 이 *.localhost 를 루프백으로 강제 해석하는 문제도 회피)
    @Value("${edu.deploy.gitea-clone-base:}")           private String giteaCloneBase;
    // Gitea push webhook 서명 검증 시크릿(4단계). 미설정 시 webhook 엔드포인트 비활성(404).
    @Value("${edu.deploy.gitea-webhook-secret:}")       private String giteaWebhookSecret;

    public boolean isReal() { return "real".equalsIgnoreCase(mode); }
    public boolean isDocker() { return "docker".equalsIgnoreCase(mode); }
    public boolean autoOnApprove() { return autoOnApprove; }
    public int hostPortBase() { return hostPortBase; }
    public String appHost() { return appHost; }
    public String subdomainBase() { return subdomainBase; }
    public String proxyNetwork() { return proxyNetwork; }
    public String dynamicDir() { return dynamicDir; }
    public String giteaHost() { return giteaHost; }
    public String giteaUser() { return giteaUser; }
    public String giteaToken() { return giteaToken; }
    public String giteaWebhookSecret() { return giteaWebhookSecret; }

    /** repoUrl 이 설정된 내부 Gitea 호스트의 레포인지 판단한다(자격 증명 주입 대상 선별). */
    public boolean isGiteaRepo(String repoUrl) {
        if (giteaHost == null || giteaHost.isBlank() || repoUrl == null) return false;
        return repoUrl.startsWith("http://" + giteaHost + "/")
                || repoUrl.startsWith("https://" + giteaHost + "/");
    }

    /**
     * 내부 Gitea 레포의 clone 실제 접근 주소로 재작성한다.
     * gitea-clone-base 미설정이거나 Gitea 레포가 아니면 원본 그대로 반환.
     */
    public String rewriteGiteaUrl(String repoUrl) {
        if (!isGiteaRepo(repoUrl) || giteaCloneBase == null || giteaCloneBase.isBlank()) return repoUrl;
        String path = repoUrl.substring(repoUrl.indexOf('/', repoUrl.indexOf("://") + 3));
        return giteaCloneBase.replaceAll("/+$", "") + path;
    }

    public String mode() { return mode; }
    public String namespace() { return namespace; }
    public String namespacePublic() { return namespacePublic; }
    public String ingressHost() { return ingressHost; }
    public String registry() { return registry; }
    public String buildNamespace() { return buildNamespace; }
    public boolean kanikoInsecure() { return kanikoInsecure; }
    public int replicas() { return replicas; }
    public String cpuLimit() { return cpuLimit; }
    public String memoryLimit() { return memoryLimit; }
}
