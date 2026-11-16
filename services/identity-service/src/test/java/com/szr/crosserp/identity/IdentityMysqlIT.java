package com.szr.crosserp.identity;

import com.szr.crosserp.identity.auth.service.AuthService;
import com.szr.crosserp.identity.user.model.User;
import com.szr.crosserp.identity.user.repository.UserRepository;
import com.szr.crosserp.identity.user.repository.RoleRepository;
import com.szr.crosserp.identity.common.exception.ApiException;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class IdentityMysqlIT {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        var pair=generator.generateKeyPair();var directory=Files.createTempDirectory("identity-it-");
        var priv=directory.resolve("private.pem");var pub=directory.resolve("public.pem");
        Files.writeString(priv,pem("PRIVATE KEY",pair.getPrivate().getEncoded()));
        Files.writeString(pub,pem("PUBLIC KEY",pair.getPublic().getEncoded()));
        priv.toFile().deleteOnExit();pub.toFile().deleteOnExit();directory.toFile().deleteOnExit();
        registry.add("app.auth.private-key",()->priv.toUri().toString());
        registry.add("app.auth.public-key",()->pub.toUri().toString());
        registry.add("app.auth.key-id",()->"mysql-it");
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:13316/cross_erp_identity_it?connectionTimeZone=UTC");
        registry.add("spring.datasource.username",()->"root");
        registry.add("spring.datasource.password",()->"identity-it-only-password");
    }
    private static String pem(String type,byte[] bytes) {
        return "-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes)+"\n-----END "+type+"-----\n";
    }
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test
    void httpLoginMeRefreshAndLogoutKeepExistingContract() throws Exception {
        var user=user();
        var login=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
            .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of(
                "loginName",user.getLoginName(),"password","test-only-password"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var payload=mapper.readTree(login.getResponse().getContentAsString());
        String token=payload.get("accessToken").asText();
        assertThat(payload.get("user").get("id").asLong()).isEqualTo(user.getId());
        var cookie=login.getResponse().getCookie("cross_erp_refresh");
        assertThat(cookie).isNotNull();assertThat(cookie.isHttpOnly()).isTrue();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
            .header("Authorization","Bearer "+token))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var refreshed=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh").cookie(cookie))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var replacement=refreshed.getResponse().getCookie("cross_erp_refresh");
        assertThat(replacement).isNotNull();assertThat(replacement.getValue()).isNotEqualTo(cookie.getValue());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout").cookie(replacement))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh").cookie(replacement))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    private User user() {
        User user=new User("it-"+UUID.randomUUID(),"测试",encoder.encode("test-only-password"),false);
        user.addRole(roles.findByCode("OWNER").orElseThrow());return users.saveAndFlush(user);
    }
    @Test
    void fiveConcurrentFailuresAreAllCounted() throws Exception {
        User user=user();var pool=Executors.newFixedThreadPool(5);var gate=new CountDownLatch(1);
        try {
            var tasks=new java.util.ArrayList<Future<String>>();
            for(int i=0;i<5;i++) tasks.add(pool.submit(()->{
                gate.await();try {auth.login(user.getLoginName(),"wrong","127.0.0.1","test");return "unexpected";}
                catch(ApiException e){return e.getCode();}
            }));
            gate.countDown();
            for(var task:tasks) assertThat(task.get(30,TimeUnit.SECONDS)).isEqualTo("AUTH_INVALID_CREDENTIALS");
            var saved=users.findById(user.getId()).orElseThrow();
            assertThat(saved.getFailedLoginCount()).isEqualTo(5);
            assertThat(saved.getLockedUntil()).isAfter(java.time.Instant.now());
        } finally {pool.shutdownNow();}
    }
    @Test
    void competingRefreshesProduceOneSuccessAndRevokeReplacementOnReplay() throws Exception {
        var user=user();var login=auth.login(user.getLoginName(),"test-only-password","127.0.0.1","test");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            Callable<String> operation=()->{gate.await();try{return auth.refresh(login.refreshToken(),"127.0.0.1","test").refreshToken();}
                catch(ApiException e){return e.getCode();}};
            var first=pool.submit(operation);var second=pool.submit(operation);gate.countDown();
            var outcomes=java.util.List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn("AUTH_REFRESH_INVALID"::equals).hasSize(1);
            String replacement=outcomes.stream().filter(s->!s.equals("AUTH_REFRESH_INVALID")).findFirst().orElseThrow();
            assertThatThrownBy(()->auth.refresh(replacement,"127.0.0.1","test")).isInstanceOf(ApiException.class);
        } finally {pool.shutdownNow();}
    }
}
