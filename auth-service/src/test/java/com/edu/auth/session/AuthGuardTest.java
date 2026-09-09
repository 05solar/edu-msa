package com.edu.auth.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edu.auth.account.domain.Account;
import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.account.repository.AccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 인증 방어 계층 검증 — 로그인 실패 제한(계정/IP), refresh 남용 차단, 데모 로그인 기본 비활성.
 * 임계값은 테스트용으로 작게, 실패 지연은 0으로 재정의한다.
 * 카운터 저장소가 컨텍스트 전역 싱글턴이므로 테스트마다 서로 다른 계정/IP 를 쓴다.
 */
@SpringBootTest(properties = {
        "edu.auth.ratelimit.account-max-failures=3",
        "edu.auth.ratelimit.account-block-seconds=60",
        "edu.auth.ratelimit.ip-max-failures=5",
        "edu.auth.ratelimit.ip-block-seconds=60",
        "edu.auth.ratelimit.refresh-max-failures=3",
        "edu.auth.ratelimit.refresh-block-seconds=60",
        "edu.auth.ratelimit.fail-delay-ms=0",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthGuardTest {

    private static final String PASSWORD = "Correct#Pw1";

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private PasswordEncoder encoder;

    private void createAccount(String username) {
        accounts.save(new Account(username, encoder.encode(PASSWORD),
                "테스트", username + "@test.local", "부서", AccountRole.USER, false));
    }

    private MockHttpServletRequestBuilder loginReq(String username, String password, String ip) {
        return post("/api/auth/login")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    @Test
    void 정상_로그인은_통과하고_성공시_실패카운터가_초기화된다() throws Exception {
        createAccount("guard-ok");
        // 실패 2회(임계 3 미만) 후 성공 → 카운터 초기화
        mvc.perform(loginReq("guard-ok", "wrong", "10.0.0.1")).andExpect(status().isUnauthorized());
        mvc.perform(loginReq("guard-ok", "wrong", "10.0.0.1")).andExpect(status().isUnauthorized());
        mvc.perform(loginReq("guard-ok", PASSWORD, "10.0.0.1")).andExpect(status().isOk());
        // 초기화됐으므로 다시 2회 실패해도 차단되지 않는다(401 이지 429 가 아님)
        mvc.perform(loginReq("guard-ok", "wrong", "10.0.0.1")).andExpect(status().isUnauthorized());
        mvc.perform(loginReq("guard-ok", "wrong", "10.0.0.1")).andExpect(status().isUnauthorized());
        mvc.perform(loginReq("guard-ok", PASSWORD, "10.0.0.1")).andExpect(status().isOk());
    }

    @Test
    void 반복_실패시_계정이_임시차단되고_다른_IP에서도_유지된다() throws Exception {
        createAccount("guard-lock");
        mvc.perform(loginReq("guard-lock", "wrong", "10.0.1.1")).andExpect(status().isUnauthorized());
        mvc.perform(loginReq("guard-lock", "wrong", "10.0.1.1")).andExpect(status().isUnauthorized());
        mvc.perform(loginReq("guard-lock", "wrong", "10.0.1.1")).andExpect(status().isUnauthorized()); // 3회 → 차단 등록
        // 이후에는 올바른 비밀번호라도 429 + Retry-After
        mvc.perform(loginReq("guard-lock", PASSWORD, "10.0.1.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // 계정 기준이므로 다른 IP 로 우회해도 차단 유지 (크리덴셜 스터핑 IP 분산 대응)
        mvc.perform(loginReq("guard-lock", PASSWORD, "10.0.1.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 같은_IP의_계정무관_반복실패는_IP기준으로_차단된다() throws Exception {
        createAccount("guard-victim");
        // 존재하지 않는 계정들로 5회 실패(IP 임계) — 계정당 3회 미만이라 계정 차단은 없음
        for (int i = 1; i <= 5; i++) {
            mvc.perform(loginReq("no-such-user-" + i, "x", "10.0.2.1")).andExpect(status().isUnauthorized());
        }
        // 같은 IP 에서 정상 계정 로그인 시도 → IP 차단으로 429
        mvc.perform(loginReq("guard-victim", PASSWORD, "10.0.2.1"))
                .andExpect(status().isTooManyRequests());
        // 다른 IP 의 같은 계정은 정상 (IP 기준 차단이 계정을 잠그지 않는다)
        mvc.perform(loginReq("guard-victim", PASSWORD, "10.0.2.200")).andExpect(status().isOk());
    }

    @Test
    void refresh_반복_실패는_IP기준으로_차단된다() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/auth/refresh")
                            .header("X-Forwarded-For", "10.0.3.1")
                            .cookie(new Cookie("edu_refresh", "forged-token-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", "10.0.3.1")
                        .cookie(new Cookie("edu_refresh", "forged-token-x")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void 데모_로그인은_기본값이_비활성이라_404를_반환한다() throws Exception {
        mvc.perform(post("/api/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isNotFound());
    }
}
