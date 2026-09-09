package com.edu.auth.ratelimit;

import com.edu.auth.common.TooManyRequestsException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 로그인/갱신 남용 방어 정책 적용.
 *
 * - 계정 기준: 같은 계정으로 window 안에 N회 초과 실패 → 짧은 임시 차단(다른 IP 에서도 적용).
 * - IP 기준: 같은 IP 의 실패 합산(크리덴셜 스터핑) → 더 관대한 임계값으로 차단.
 * - 실패 응답 지연(지수 백오프, 상한 有) — 자동화 공격의 시도율을 낮춘다.
 * - refresh: 위조·만료 토큰의 반복 제출을 IP 기준으로 차단.
 *
 * 트랜잭션 밖(컨트롤러 계층)에서 호출한다 — 지연(sleep)이 DB 커넥션을 잡지 않게.
 */
@Component
public class LoginGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginGuard.class);

    private final AttemptStore store;
    private final RateLimitProperties props;

    public LoginGuard(AttemptStore store, RateLimitProperties props) {
        this.store = store;
        this.props = props;
    }

    /** 로그인 시도 전 차단 여부 검사 — 차단 중이면 429. */
    public void checkLogin(String ip, String username) {
        if (!props.isEnabled()) return;
        long remain = Math.max(
                store.blockedRemainingSeconds(accountKey(username)),
                store.blockedRemainingSeconds(ipKey(ip)));
        if (remain > 0) {
            throw new TooManyRequestsException(
                    "로그인 시도가 너무 많습니다. " + remain + "초 후 다시 시도해 주세요.", remain);
        }
    }

    /** 로그인 실패 반영 — 카운트 증가, 임계 초과 시 차단 등록, 응답 지연. */
    public void onLoginFailure(String ip, String username) {
        if (!props.isEnabled()) return;
        long accountFails = store.increment(accountKey(username), props.getAccountWindowSeconds());
        long ipFails = store.increment(ipKey(ip), props.getIpWindowSeconds());

        if (accountFails >= props.getAccountMaxFailures()) {
            store.block(accountKey(username), props.getAccountBlockSeconds());
            log.warn("로그인 실패 임계 초과 — 계정 임시 차단 {}초 (username={}, ip={}, fails={})",
                    props.getAccountBlockSeconds(), username, ip, accountFails);
        }
        if (ipFails >= props.getIpMaxFailures()) {
            store.block(ipKey(ip), props.getIpBlockSeconds());
            log.warn("로그인 실패 임계 초과 — IP 임시 차단 {}초 (ip={}, fails={})",
                    props.getIpBlockSeconds(), ip, ipFails);
        }
        delay(accountFails);
    }

    /** 로그인 성공 — 계정 실패 카운터만 초기화(IP 합산 카운터는 윈도우 만료로 정리). */
    public void onLoginSuccess(String ip, String username) {
        if (!props.isEnabled()) return;
        store.reset(accountKey(username));
    }

    /** refresh 시도 전 차단 여부 검사. */
    public void checkRefresh(String ip) {
        if (!props.isEnabled()) return;
        long remain = store.blockedRemainingSeconds(refreshKey(ip));
        if (remain > 0) {
            throw new TooManyRequestsException(
                    "요청이 너무 많습니다. " + remain + "초 후 다시 시도해 주세요.", remain);
        }
    }

    /** refresh 실패(위조·만료·폐기 토큰) 반영 — IP 기준 카운트, 임계 초과 시 차단. */
    public void onRefreshFailure(String ip) {
        if (!props.isEnabled()) return;
        long fails = store.increment(refreshKey(ip), props.getRefreshWindowSeconds());
        if (fails >= props.getRefreshMaxFailures()) {
            store.block(refreshKey(ip), props.getRefreshBlockSeconds());
            log.warn("refresh 실패 임계 초과 — IP 임시 차단 {}초 (ip={}, fails={})",
                    props.getRefreshBlockSeconds(), ip, fails);
        }
    }

    /** 실패 횟수에 따른 지수 백오프 지연(상한 有). */
    private void delay(long failures) {
        long base = props.getFailDelayMs();
        if (base <= 0) return;
        long shift = Math.min(Math.max(failures - 1, 0), 8);   // 2^8 상한으로 오버플로 방지
        long ms = Math.min(base * (1L << shift), props.getMaxFailDelayMs());
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String accountKey(String username) {
        return "login:acct:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private String ipKey(String ip) {
        return "login:ip:" + ip;
    }

    private String refreshKey(String ip) {
        return "refresh:ip:" + ip;
    }
}
