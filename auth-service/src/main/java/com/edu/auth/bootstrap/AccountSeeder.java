package com.edu.auth.bootstrap;

import com.edu.auth.account.domain.Account;
import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.account.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 backend 의 데모 계정(USERS_SEED 7명)을 auth-db 로 이관한다.
 * 이름·부서·역할은 기존 값을 그대로 유지하고, 자격 증명은 여기서 새로 만든다.
 *
 * 평문 비밀번호는 저장하지 않는다. 공통 임시 비밀번호(EDU_SEED_PASSWORD)를 BCrypt 로 해시해
 * 넣고 mustChangePassword=true 로 표시하며, 최초 로그인 시 변경 강제는 추후 작업으로 남긴다.
 *
 * 이미 같은 아이디가 있으면 건드리지 않으므로 재기동해도 안전하다.
 */
@Component
public class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);
    private static final String SEED_FILE = "seed/accounts.json";
    private static final String EMAIL_DOMAIN = "edu.local";

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String seedPassword;

    public AccountSeeder(AccountRepository accounts, PasswordEncoder passwordEncoder,
                         ObjectMapper objectMapper,
                         @Value("${edu.seed.enabled}") boolean enabled,
                         @Value("${edu.seed.password}") String seedPassword) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.seedPassword = seedPassword;
    }

    /** seed/accounts.json 한 줄에 대응. */
    public record SeedAccount(String username, String name, String dept, String role) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("계정 시드가 비활성화되어 있습니다. (edu.seed.enabled=false)");
            return;
        }

        List<SeedAccount> seeds;
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            seeds = List.of(objectMapper.readValue(in, SeedAccount[].class));
        }

        String hash = passwordEncoder.encode(seedPassword);
        int created = 0;

        for (SeedAccount s : seeds) {
            if (accounts.existsByUsername(s.username())) continue;

            String email = s.username() + "@" + EMAIL_DOMAIN;
            if (accounts.existsByEmailIgnoreCase(email)) continue;

            accounts.save(new Account(
                    s.username(), hash, s.name(), email, s.dept(),
                    AccountRole.from(s.role()), true));
            created++;
        }

        if (created > 0) {
            log.info("데모 계정 {}건을 auth-db 로 이관했습니다. (임시 비밀번호는 EDU_SEED_PASSWORD)", created);
        } else {
            log.info("이관할 신규 데모 계정이 없습니다. (기존 {}건 유지)", accounts.count());
        }
    }
}
