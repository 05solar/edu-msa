package com.edu.auth.account.dto;

import com.edu.auth.account.domain.Account;
import com.edu.auth.account.domain.AccountRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 계정 관련 요청/응답. 검증 규칙은 프론트엔드 pages/Auth/validation.ts 와 동일하게 맞춘다.
 */
public final class AccountDtos {

    private AccountDtos() {}

    /** 아이디: 영문 소문자로 시작하는 4~20자 (영문 소문자·숫자·밑줄) */
    public static final String USERNAME_PATTERN = "^[a-z][a-z0-9_]{3,19}$";

    /** 비밀번호: 8~64자 + 영문·숫자·특수문자 각 1자 이상 */
    public static final String PASSWORD_PATTERN =
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,64}$";

    public record SignupRequest(
            @NotBlank(message = "아이디를 입력해 주세요.")
            @Pattern(regexp = USERNAME_PATTERN,
                    message = "아이디는 영문 소문자로 시작하는 4~20자이며, 영문 소문자·숫자·밑줄(_)만 사용합니다.")
            String username,

            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Pattern(regexp = PASSWORD_PATTERN,
                    message = "비밀번호는 8~64자이며 영문·숫자·특수문자를 모두 포함해야 합니다.")
            String password,

            @NotBlank(message = "이름을 입력해 주세요.")
            @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
            String name,

            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 190, message = "이메일은 190자를 넘을 수 없습니다.")
            String email,

            @NotBlank(message = "부서를 입력해 주세요.")
            @Size(max = 50, message = "부서는 50자를 넘을 수 없습니다.")
            String dept
    ) {}

    /** 계정 표현 — 비밀번호 해시는 어떤 응답에도 포함하지 않는다. */
    public record AccountResponse(
            Long id,
            String username,
            String name,
            String email,
            String dept,
            AccountRole role,
            boolean mustChangePassword
    ) {
        public static AccountResponse of(Account a) {
            return new AccountResponse(a.getId(), a.getUsername(), a.getName(), a.getEmail(),
                    a.getDept(), a.getRole(), a.isMustChangePassword());
        }
    }

    /** GET /api/auth/check-duplicate 응답 */
    public record DuplicateResponse(String field, String value, boolean available) {}

    public record RoleChangeRequest(
            @NotBlank(message = "권한을 입력해 주세요.")
            String role
    ) {}
}
