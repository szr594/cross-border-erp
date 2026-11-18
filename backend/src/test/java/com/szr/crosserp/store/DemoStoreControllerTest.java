package com.szr.crosserp.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DemoStoreControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Test void ownerInitializesIdempotentlyAndReenablesWithoutOrders() throws Exception {
        var auth=jwt().jwt(j->j.subject("1").claim("roles",java.util.List.of("OWNER")));
        String path="/api/stores/demo/initialize";
        mvc.perform(post(path).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("DEMO-STORE"));
        Long id=jdbc.queryForObject("select id from sales_stores where code='DEMO-STORE'",Long.class);
        jdbc.update("update sales_stores set status='INACTIVE' where id=?",id);
        mvc.perform(post(path).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.storeId").value(id)).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(jdbc.queryForObject("select count(*) from sales_stores where code='DEMO-STORE'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sales_orders",Long.class)).isZero();
    }
    @Test void otherRolesAndAnonymousCannotInitialize() throws Exception {
        for(String role:java.util.List.of("OPERATIONS","PROCUREMENT","WAREHOUSE","PACKING"))
            mvc.perform(post("/api/stores/demo/initialize").with(jwt().jwt(j->j.claim("roles",java.util.List.of(role)))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stores/demo/initialize")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from sales_stores where code='DEMO-STORE'",Long.class)).isZero();
    }
    @Test void initializedStoreAcceptsDemoCsvAndRepeatedConfirmationDoesNotDuplicateOrders() throws Exception {
        jdbc.update("insert into app_users(id,login_name,display_name,password_hash,status,must_change_password) values(98123,'csv-demo-owner','演示老板','unused-test-hash','ACTIVE',false)");
        var auth=jwt().jwt(j->j.subject("98123").claim("roles",java.util.List.of("OWNER")));
        mvc.perform(post("/api/stores/demo/initialize").with(auth)).andExpect(status().isOk());
        byte[] csv;
        try(var input=getClass().getResourceAsStream("/fixtures/orders-demo.csv")) {
            org.junit.jupiter.api.Assertions.assertNotNull(input); csv=input.readAllBytes();
        }
        var file=new org.springframework.mock.web.MockMultipartFile("file","orders-demo.csv","text/csv",csv);
        var preview=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/order-imports/preview")
            .file(file).with(auth)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("VALIDATED")).andReturn();
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(preview.getResponse().getContentAsString());
        String path="/api/order-imports/"+json.path("importId").asText()+"/confirm";
        mvc.perform(post(path).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(post(path).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("select count(*) from sales_orders",Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from sales_order_items",Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select sum(quantity) from sales_order_items",Long.class)).isEqualTo(10);
    }
}
