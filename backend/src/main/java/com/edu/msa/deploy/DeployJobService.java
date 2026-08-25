package com.edu.msa.deploy;

import com.edu.msa.common.DeployJobStatus;
import com.edu.msa.deploy.domain.DeployJob;
import com.edu.msa.deploy.dto.DeployDtos.DeployJobResponse;
import com.edu.msa.deploy.repository.DeployJobRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배포 작업 큐: 적재(enqueue) / 선점(claim) / 완료·재시도(complete). */
@Service
public class DeployJobService {

    private final DeployJobRepository repo;

    public DeployJobService(DeployJobRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public DeployJobResponse enqueue(Long programId, String repoUrl, String branch, String actor) {
        return toResponse(repo.save(new DeployJob(programId, repoUrl, branch, actor)));
    }

    /** 대기 작업 하나를 선점해 RUNNING 으로 바꿔 반환한다(없으면 null). 행 잠금으로 중복 처리 방지. */
    @Transactional
    public DeployJob claimNext() {
        Long id = repo.claimNextId();
        if (id == null) return null;
        DeployJob j = repo.findById(id).orElse(null);
        if (j == null) return null;
        j.setStatus(DeployJobStatus.RUNNING);
        j.setAttempts(j.getAttempts() + 1);
        j.touch();
        return repo.save(j);
    }

    /** 처리 결과 반영. 실패면 재시도 한도 내에서 다시 QUEUED, 초과 시 FAILED. */
    @Transactional
    public void complete(Long jobId, boolean success, Long deploymentId, String error) {
        DeployJob j = repo.findById(jobId).orElse(null);
        if (j == null) return;
        if (success) {
            j.setStatus(DeployJobStatus.DONE);
            j.setDeploymentId(deploymentId);
            j.setLastError(null);
        } else if (j.getAttempts() < j.getMaxAttempts()) {
            j.setStatus(DeployJobStatus.QUEUED);   // 재시도
            j.setLastError(error);
        } else {
            j.setStatus(DeployJobStatus.FAILED);
            j.setLastError(error);
        }
        j.touch();
        repo.save(j);
    }

    @Transactional(readOnly = true)
    public List<DeployJobResponse> list() {
        return repo.findAllByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    private DeployJobResponse toResponse(DeployJob j) {
        return new DeployJobResponse(j.getId(), j.getProgramId(), j.getRepoUrl(), j.getBranch(),
                j.getStatus(), j.getAttempts(), j.getDeploymentId(), j.getLastError());
    }
}
