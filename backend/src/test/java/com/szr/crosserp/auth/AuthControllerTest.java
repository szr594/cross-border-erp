package com.szr.crosserp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.szr.crosserp.auth.repository.RefreshSessionRepository;
import com.szr.crosserp.user.model.Role;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.RoleRepository;
import com.szr.crosserp.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RefreshSessionRepository sessionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        Role ownerRole = roleRepository.save(new Role("OWNER", "老板"));
        User owner = new User("owner", "演示管理员", passwordEncoder.encode("correct-password"), false);
        owner.addRole(ownerRole);
        userRepository.save(owner);
    }

    @Test
    void loginRefreshCurrentUserAndLogout() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"loginName":"owner","password":"correct-password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly("cross_erp_refresh", true))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.loginName").value("owner"))
            .andReturn();

        String accessToken = JsonTestSupport.string(loginResult, "/accessToken");
        Cookie refreshCookie = loginResult.getResponse().getCookie("cross_erp_refresh");
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("OWNER"));

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly("cross_erp_refresh", true))
            .andReturn();
        Cookie rotatedCookie = refreshResult.getResponse().getCookie("cross_erp_refresh");
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));

        mockMvc.perform(post("/api/auth/refresh").cookie(rotatedCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));

        mockMvc.perform(post("/api/auth/logout").cookie(rotatedCookie))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge("cross_erp_refresh", 0));
    }

    @Test
    void invalidPasswordLocksAccountAfterFiveFailures() throws Exception {
        for (int attempt = 0; attempt < 5; attempt += 1) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"loginName":"owner","password":"wrong-password"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"loginName":"owner","password":"correct-password"}
                    """))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }
}
