package com.edu.auth.session.repository;

import com.edu.auth.session.domain.RefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 회전 소비를 원자적으로 수행한다(P1-3) — usable(미폐기) 상태에서만 폐기로 전환.
     * 반환 1 = 이 요청이 소비에 성공(승자), 0 = 다른 요청이 방금 소비함(동시 경쟁 패자).
     * 동일 토큰의 동시 refresh 는 DB 의 조건부 UPDATE 행 잠금이 최종 심판이라
     * replica 몇 개에서든 정확히 한 요청만 승자가 된다(JVM 락 불사용).
     */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true, t.revokedAt = :now "
            + "where t.id = :id and t.revoked = false")
    int consumeIfUsable(@Param("id") Long id, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true, t.revokedAt = :now "
            + "where t.accountId = :accountId and t.revoked = false")
    int revokeAllByAccountId(@Param("accountId") Long accountId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);

    /**
     * 만료 후 보존 기간까지 지난 행을 배치 단위로 삭제한다(RefreshTokenCleaner 가 반복 호출).
     * LIMIT 서브쿼리로 한 번에 지우는 양을 제한해 대량 백로그에서도 긴 잠금이 없고,
     * 삭제는 멱등이라 여러 replica 가 동시에 돌아도 서로 0건을 지울 뿐 충돌하지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM refresh_tokens WHERE id IN "
            + "(SELECT id FROM refresh_tokens WHERE expires_at < :cutoff LIMIT :batch)",
            nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") OffsetDateTime cutoff, @Param("batch") int batch);
}
