package com.edu.msa.deploy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 배포 파이프라인 설정 (edu.deploy.*). */
@Component
public class DeployProperties {
    @Value("${edu.deploy.mode:simulate}")      private String mode;          // simulate | docker | real
    @Value("${edu.deploy.namespace:edu-services}") private String namespace;
    @Value("${edu.deploy.ingress-host:edu.internal}") private String ingressHost;
    @Value("${edu.deploy.registry:registry.edu.internal}") private String registry;
    @Value("${edu.deploy.replicas:1}")         private int replicas;
    @Value("${edu.deploy.cpu-limit:500m}")     private String cpuLimit;
    @Value("${edu.deploy.memory-limit:512Mi}") private String memoryLimit;
    // 로컬 Docker 실배포 모드용
    @Value("${edu.deploy.host-port-base:31000}") private int hostPortBase;
    @Value("${edu.deploy.app-host:localhost}")   private String appHost;
    @Value("${edu.deploy.auto-on-approve:true}") private boolean autoOnApprove;

    public boolean isReal() { return "real".equalsIgnoreCase(mode); }
    public boolean isDocker() { return "docker".equalsIgnoreCase(mode); }
    public boolean autoOnApprove() { return autoOnApprove; }
    public int hostPortBase() { return hostPortBase; }
    public String appHost() { return appHost; }
    public String mode() { return mode; }
    public String namespace() { return namespace; }
    public String ingressHost() { return ingressHost; }
    public String registry() { return registry; }
    public int replicas() { return replicas; }
    public String cpuLimit() { return cpuLimit; }
    public String memoryLimit() { return memoryLimit; }
}
