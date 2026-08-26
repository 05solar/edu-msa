package com.edu.auth.session;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시연용 데모 로그인 설정.
 *
 * 데모 버튼은 비밀번호 없이 미리 정해진 데모 계정의 토큰을 받는다.
 * 공통 임시 비밀번호를 프론트엔드에 심지 않기 위한 것이며,
 * 시연이 필요 없는 환경에서는 enabled 를 꺼서 엔드포인트 자체를 막는다.
 */
@ConfigurationProperties(prefix = "edu.auth.demo")
public class DemoProperties {

    /** 데모 로그인 허용 여부. 운영 환경에서는 false 로 주입한다. */
    private boolean enabled = true;

    /** 역할 코드(user/coder/admin) → 데모 계정 아이디. */
    private Map<String, String> accounts = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, String> getAccounts() { return accounts; }
    public void setAccounts(Map<String, String> accounts) { this.accounts = accounts; }
}
