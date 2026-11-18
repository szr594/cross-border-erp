package com.szr.crosserp.procurement;

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
class PurchaseOrderingControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    void seed() {
        jdbc.update("insert into app_users(id,login_name,display_name,password_hash,status,must_change_password) values(98401,'ordering-test','Test','unused','ACTIVE',false)");
        jdbc.update("insert into suppliers(id,code,name,platform,status) values(98401,'ORDERING-TEST','Test','MANUAL','ACTIVE')");
        jdbc.update("insert into purchase_orders(id,purchase_order_no,supplier_id,status,currency,created_by) values(98401,'ORDERING-TEST',98401,'DRAFT','CNY',98401)");
    }
    @Test void confirmationAndSameVersionReplayRecordOneEvent() throws Exception {
        seed();var auth=jwt().jwt(j->j.subject("98401").claim("roles",java.util.List.of("OWNER")));
        for(int i=0;i<2;i++)mvc.perform(post("/api/purchase-orders/98401/confirm-order").with(auth).contentType("application/json").content("{\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDERED")).andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select ordered_by from purchase_orders where id=98401",Long.class)).isEqualTo(98401);
        assertThat(jdbc.queryForObject("select count(*) from purchase_order_events where purchase_order_id=98401 and event_type='ORDERED'",Long.class)).isEqualTo(1);
    }
    @Test void permissionVersionAndBridgeChecksPreventChanges() throws Exception {
        seed();
        mvc.perform(post("/api/purchase-orders/98401/confirm-order").with(jwt().jwt(j->j.subject("98401").claim("roles",java.util.List.of("OPERATIONS")))).contentType("application/json").content("{\"version\":0}")).andExpect(status().isForbidden());
        var auth=jwt().jwt(j->j.subject("98401").claim("roles",java.util.List.of("PROCUREMENT")));
        mvc.perform(post("/api/purchase-orders/98401/confirm-order").with(auth).contentType("application/json").content("{\"version\":9}")).andExpect(status().isConflict());
        jdbc.update("update purchase_orders set bridge_confirmed=false where id=98401"); entityManager.clear();
        mvc.perform(post("/api/purchase-orders/98401/confirm-order").with(auth).contentType("application/json").content("{\"version\":0}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from purchase_orders where id=98401",String.class)).isEqualTo("DRAFT");
    }
}
