package com.edu.msa.gitea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class GiteaDtos {
    private GiteaDtos() {}

    /**
     * 발급 요청. 아이디는 Gitea 규칙(영문/숫자/._-)에 맞춰 영문으로 시작하는 3~39자만 허용.
     * 비밀번호는 Gitea 기본 최소 길이(8자) 이상 — 정책 강화 시 Gitea 쪽 오류를 그대로 전달한다.
     */
    public record CreateRequest(
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9._-]{2,38}$",
                    message = "영문으로 시작하는 3~39자의 영문/숫자/._- 만 사용할 수 있습니다.")
            String username,
            @NotBlank @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다.")
            String password) {}

    /**
     * 내 발급 상태. enabled=false 면 서버에 Gitea 연동이 구성되지 않은 것(UI 는 섹션 숨김).
     * host 는 스킴 없는 공개 접속 호스트 — UI 가 프로토콜 상대 링크(//host)로 연결한다.
     */
    public record StatusResponse(boolean enabled, boolean issued, String username, String host) {}

    /** 내 Gitea 레포 — url 은 공개 웹 주소(html_url, 등록 화면에 그대로 사용 가능). */
    public record RepoView(String name, String url, boolean isPrivate, String updatedAt) {}
}
