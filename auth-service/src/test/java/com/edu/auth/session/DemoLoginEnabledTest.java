package com.edu.auth.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edu.auth.account.domain.Account;
import com.edu.auth.account.domain.AccountRole;
import com.edu.auth.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 데모 로그인은 명시적으로 켠 환경(EDU_DEMO_LOGIN=true)에서만 동작한다. */
@SpringBootTest(properties = {
        "edu.auth.demo.enabled=true",
        "edu.auth.demo.accounts.admin=demo-admin-acct",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoLoginEnabledTest {

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private PasswordEncoder encoder;

    @Test
    void 명시적으로_켠_환경에서는_데모_로그인이_동작한다() throws Exception {
        accounts.save(new Account("demo-admin-acct", encoder.encode("unused#Pw1"),
                "데모관리자", "demo-admin@test.local", "운영", AccountRole.ADMIN, false));

        mvc.perform(post("/api/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }
}
