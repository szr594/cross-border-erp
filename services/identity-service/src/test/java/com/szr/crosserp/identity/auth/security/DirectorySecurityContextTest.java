package com.szr.crosserp.identity.auth.security;

import com.szr.crosserp.identity.auth.config.DirectorySecurity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import static org.assertj.core.api.Assertions.*;

class DirectorySecurityContextTest {
    @Configuration(proxyBeanMethods=false)
    @EnableWebSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class SecurityInfrastructure {}
    @Test void configurationAndFilterChainRegisterWithoutBeanNameCollision() {
        new WebApplicationContextRunner().withUserConfiguration(SecurityInfrastructure.class,DirectorySecurity.class)
            .run(context->{
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DirectorySecurity.class);
                assertThat(context.getBean("directorySecurityFilterChain")).isInstanceOf(SecurityFilterChain.class);
            });
    }
}
