package com.edu.auth.session;

import com.edu.auth.session.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 폐기를 별도 트랜잭션으로 수행한다.
 *
 * 폐기된 Refresh Token 이 다시 제출되면 탈취 가능성이 있어 해당 계정의 세션을 모두 끊는데,
 * 그 직후 401 예외를 던지면 같은 트랜잭션에 묶인 폐기까지 롤백된다.
 * REQUIRES_NEW 로 분리해 폐기는 반드시 커밋되도록 한다.
 */
@Component
public class SessionRevoker {

    private final RefreshTokenRepository refreshTokens;

    public SessionRevoker(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllOf(Long accountId) {
        refreshTokens.revokeAllByAccountId(accountId);
    }
}
