package com.szr.crosserp.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
import com.szr.crosserp.procurement.model.SupplierStatus;
import com.szr.crosserp.procurement.repository.SupplierRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupplierControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired SupplierRepository supplierRepository;

    @Test
    void allEmployeesCanListAndViewSupplierContactDetails() throws Exception {
        String suffix = suffix();
        Supplier supplier = supplierRepository.saveAndFlush(new Supplier(
            "SUP-" + suffix, "深圳演示供应商", SupplierPlatform.MANUAL,
            null, "张三", "13800138000", "https://example.test/shop", "长期合作"
        ));

        mockMvc.perform(get("/api/suppliers")
                .param("keyword", "深圳")
                .with(actor("WAREHOUSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(supplier.getId()))
            .andExpect(jsonPath("$.items[0].contactPhone").value("13800138000"));

        mockMvc.perform(get("/api/suppliers/{supplierId}", supplier.getId()).with(actor("OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("深圳演示供应商"))
            .andExpect(jsonPath("$.contactName").value("张三"))
            .andExpect(jsonPath("$.contactPhone").value("13800138000"));
    }

    @Test
    void ownerCreatesSupplierAndDuplicateCodeReturnsConflict() throws Exception {
        String code = "SUP-" + suffix();
        String body = """
            {"code":"%s","name":"义乌供应商","platform":"ALIBABA_1688",
             "externalSupplierId":"1688-1001","contactName":"李四","contactPhone":"13900139000",
             "shopUrl":"https://detail.1688.com/shop","note":"线上供应商"}
            """.formatted(code);

        mockMvc.perform(post("/api/suppliers")
                .with(actor("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(code.toUpperCase()))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(post("/api/suppliers")
                .with(actor("PROCUREMENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SUPPLIER_DUPLICATE"));
    }

    @Test
    void procurementUpdatesAndDisablesSupplierUsingOptimisticVersion() throws Exception {
        Supplier supplier = supplierRepository.saveAndFlush(
            new Supplier("SUP-" + suffix(), "原供应商", SupplierPlatform.MANUAL)
        );

        mockMvc.perform(put("/api/suppliers/{supplierId}", supplier.getId())
                .with(actor("PROCUREMENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"更新后的供应商","platform":"MANUAL","contactName":"王五",
                     "contactPhone":"13700137000","shopUrl":null,"note":"已复核","version":0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("更新后的供应商"))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/suppliers/{supplierId}/disable", supplier.getId())
                .with(actor("PROCUREMENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INACTIVE"))
            .andExpect(jsonPath("$.version").value(2));

        assertThat(supplierRepository.findById(supplier.getId()).orElseThrow().getStatus())
            .isEqualTo(SupplierStatus.INACTIVE);
    }

    @Test
    void operationsCannotMutateSupplierAndStaleVersionDoesNotChangeData() throws Exception {
        Supplier supplier = supplierRepository.saveAndFlush(
            new Supplier("SUP-" + suffix(), "受保护供应商", SupplierPlatform.MANUAL)
        );

        mockMvc.perform(post("/api/suppliers/{supplierId}/disable", supplier.getId())
                .with(actor("OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPPLIER_COMMAND_FORBIDDEN"));

        mockMvc.perform(put("/api/suppliers/{supplierId}", supplier.getId())
                .with(actor("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"不应保存","platform":"MANUAL","version":9}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SUPPLIER_VERSION_CONFLICT"));

        assertThat(supplierRepository.findById(supplier.getId()).orElseThrow().getName())
            .isEqualTo("受保护供应商");
    }

    @Test
    void validatesPagingAndReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/suppliers").param("size", "101").with(actor("OWNER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SUPPLIER_QUERY_INVALID"));

        mockMvc.perform(get("/api/suppliers/{supplierId}", Long.MAX_VALUE).with(actor("OWNER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
    }

    private JwtRequestPostProcessor actor(String role) {
        return jwt().jwt(token -> token.subject("1").claim("roles", List.of(role)));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
