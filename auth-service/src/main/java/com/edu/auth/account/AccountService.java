package com.edu.auth.account;

import com.edu.auth.account.domain.Account;
import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.account.dto.AccountDtos.AccountResponse;
import com.edu.auth.account.dto.AccountDtos.DuplicateResponse;
import com.edu.auth.account.dto.AccountDtos.SignupRequest;
import com.edu.auth.account.repository.AccountRepository;
import com.edu.auth.common.ConflictException;
import com.edu.auth.common.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 등록/조회. 회원가입 흐름은
 * 입력값 검증(DTO Bean Validation) → 아이디·이메일 중복 확인 → BCrypt 해시 → auth-db 저장 이다.
 */
@Service
public class AccountService {

    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_EMAIL = "email";

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AccountResponse signup(SignupRequest req) {
        String username = req.username().trim();
        String email = req.email().trim();

        if (accounts.existsByUsername(username)) {
            throw new ConflictException("이미 사용 중인 아이디입니다.");
        }
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("이미 사용 중인 이메일입니다.");
        }

        // 회원가입 기본 권한은 USER. CODER/ADMIN 은 운영 관리자가 별도로 부여한다.
        Account account = new Account(
                username,
                passwordEncoder.encode(req.password()),
                req.name().trim(),
                email,
                req.dept().trim(),
                AccountRole.USER,
                false);

        return AccountResponse.of(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public DuplicateResponse checkDuplicate(String field, String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("확인할 값을 입력해 주세요.");
        }
        boolean available = switch (field) {
            case FIELD_USERNAME -> !accounts.existsByUsername(v);
            case FIELD_EMAIL -> !accounts.existsByEmailIgnoreCase(v);
            default -> throw new IllegalArgumentException(
                    "확인할 수 없는 항목입니다: " + field + " (username 또는 email)");
        };
        return new DuplicateResponse(field, v, available);
    }

    @Transactional(readOnly = true)
    public Account getByUsername(String username) {
        return accounts.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("계정을 찾을 수 없습니다: " + username));
    }

    @Transactional(readOnly = true)
    public Account getById(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("계정을 찾을 수 없습니다: " + id));
    }

    /** 운영 관리자 전용 — CODER/ADMIN 권한 부여. */
    @Transactional
    public AccountResponse changeRole(String username, AccountRole role) {
        Account account = getByUsername(username);
        account.changeRole(role);
        return AccountResponse.of(account);
    }
}
