package com.edu.auth.account;

import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.account.dto.AccountDtos.AccountResponse;
import com.edu.auth.account.dto.AccountDtos.DuplicateResponse;
import com.edu.auth.account.dto.AccountDtos.RoleChangeRequest;
import com.edu.auth.account.dto.AccountDtos.SignupRequest;
import com.edu.auth.account.repository.AccountRepository;
import com.edu.auth.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final AccountService accountService;
    private final AccountRepository accounts;

    public AccountController(AccountService accountService, AccountRepository accounts) {
        this.accountService = accountService;
        this.accounts = accounts;
    }

    @PostMapping("/signup")
    public ResponseEntity<AccountResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.signup(req));
    }

    /** GET /api/auth/check-duplicate?field=username&value=hongildong */
    @GetMapping("/check-duplicate")
    public DuplicateResponse checkDuplicate(@RequestParam String field,
                                            @RequestParam String value) {
        return accountService.checkDuplicate(field, value);
    }

    /** 현재 로그인한 사용자 — 권한 변경이 즉시 반영되도록 토큰 클레임이 아닌 DB 를 조회한다. */
    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return AccountResponse.of(accountService.getById(principal.id()));
    }

    /** 운영 관리자 전용 — 계정 목록. */
    @GetMapping("/accounts")
    public List<AccountResponse> list() {
        return accounts.findAll().stream().map(AccountResponse::of).toList();
    }

    /** 운영 관리자 전용 — CODER/ADMIN 권한 부여. */
    @PatchMapping("/accounts/{username}/role")
    public AccountResponse changeRole(@PathVariable String username,
                                      @Valid @RequestBody RoleChangeRequest req) {
        return accountService.changeRole(username, AccountRole.from(req.role()));
    }
}
