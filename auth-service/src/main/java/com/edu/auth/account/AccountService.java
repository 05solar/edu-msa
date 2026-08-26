package com.edu.auth.account;

import com.edu.auth.account.domain.Account;
import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.account.dto.AccountDtos.AccountResponse;
import com.edu.auth.account.dto.AccountDtos.DuplicateResponse;
import com.edu.auth.account.dto.AccountDtos.SignupRequest;
import com.edu.auth.account.repository.AccountRepository;
import com.edu.auth.common.ConflictException;
import com.edu.auth.common.NotFoundException;
import java.util.List;
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

        // 회원가입 기본 권한은 항상 USER. 상향 권한(CODER/ADMIN)은 "신청"으로만 보관하고
        // 운영 관리자가 승인해야 실제 role 로 반영된다.
        Account account = new Account(
                username,
                passwordEncoder.encode(req.password()),
                req.name().trim(),
                email,
                req.dept().trim(),
                AccountRole.USER,
                false);

        AccountRole requested = parseRequestedRole(req.requestRole());
        if (requested != null) {
            String reason = req.requestReason() == null ? null : req.requestReason().trim();
            account.requestRole(requested, (reason == null || reason.isBlank()) ? null : reason);
        }

        return AccountResponse.of(accounts.save(account));
    }

    /** 신청 가능한 상향 권한만 허용(CODER/ADMIN). 비어 있거나 user 면 신청 없음(null 반환). */
    private AccountRole parseRequestedRole(String value) {
        if (value == null || value.isBlank()) return null;
        AccountRole role = AccountRole.from(value); // 알 수 없는 값이면 IllegalArgumentException(400)
        return role == AccountRole.USER ? null : role;
    }

    /** 운영 관리자 전용 — 상향 권한 승인 대기 목록(신청 순). */
    @Transactional(readOnly = true)
    public List<AccountResponse> pendingRoleRequests() {
        return accounts.findByRequestedRoleIsNotNullOrderByCreatedAtAsc()
                .stream().map(AccountResponse::of).toList();
    }

    /** 운영 관리자 전용 — 신청 승인: 신청한 권한으로 상향 후 신청 소거. */
    @Transactional
    public AccountResponse approveRoleRequest(String username) {
        Account account = getByUsername(username);
        AccountRole requested = account.getRequestedRole();
        if (requested == null) {
            throw new ConflictException("대기 중인 권한 신청이 없습니다.");
        }
        account.changeRole(requested);
        account.clearRoleRequest();
        return AccountResponse.of(account);
    }

    /** 운영 관리자 전용 — 신청 반려: 신청만 소거(권한은 USER 유지). */
    @Transactional
    public AccountResponse rejectRoleRequest(String username) {
        Account account = getByUsername(username);
        if (account.getRequestedRole() == null) {
            throw new ConflictException("대기 중인 권한 신청이 없습니다.");
        }
        account.clearRoleRequest();
        return AccountResponse.of(account);
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
