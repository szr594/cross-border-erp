package com.szr.crosserp.receiving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierPlatform;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.procurement.repository.SupplierRepository;
import com.szr.crosserp.receiving.model.DomesticPackage;
import com.szr.crosserp.receiving.model.DomesticPackageStatus;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceivingControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired GoodsReceiptRepository receiptRepository;

    private User warehouse;

    @BeforeEach
    void setUp() {
        String suffix = suffix();
        warehouse = userRepository.save(new User(
            "warehouse-" + suffix, "仓库员工", "not-used-in-this-test", false
        ));
    }

    @Test
    void warehouseRegistersAndLooksUpPackage() throws Exception {
        Fixture fixture = purchaseFixture(3);

        mockMvc.perform(post("/api/domestic-packages")
                .with(actor("WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "purchaseOrderId": %d,
                      "trackingNumber": "SF-LOOKUP-%s",
                      "carrier": "sf_express",
                      "note": "第一箱"
                    }
                    """.formatted(fixture.order().getId(), fixture.suffix())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("EXPECTED"));

        mockMvc.perform(get("/api/domestic-packages/lookup")
                .param("trackingNumber", "sf-lookup-" + fixture.suffix())
                .with(actor("OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchaseOrderNo").value(fixture.order().getPurchaseOrderNo()))
            .andExpect(jsonPath("$.items[0].remainingQuantity").value(3));
    }

    @Test
    void operationsCannotRegisterPackage() throws Exception {
        Fixture fixture = purchaseFixture(1);
        mockMvc.perform(post("/api/domestic-packages")
                .with(actor("OPERATIONS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"purchaseOrderId":%d,"trackingNumber":"NOPE-%s","carrier":"SF"}
                    """.formatted(fixture.order().getId(), fixture.suffix())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("RECEIVING_FORBIDDEN"));
    }

    @Test
    void partialReceiptUpdatesAcceptedQuantityAndPurchaseStatus() throws Exception {
        Fixture fixture = packageFixture(4);
        String request = receiptJson(fixture.item().getId(), 2, 0, "idem-" + fixture.suffix(), "正常到货");

        mockMvc.perform(post("/api/domestic-packages/{packageId}/receipts", fixture.domesticPackage().getId())
                .with(actor("WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.packageStatus").value("RECEIVING"))
            .andExpect(jsonPath("$.purchaseOrderStatus").value("PARTIALLY_RECEIVED"))
            .andExpect(jsonPath("$.inventoryPosted").value(false));

        assertThat(purchaseOrderItemRepository.findById(fixture.item().getId()).orElseThrow().getReceivedQuantity())
            .isEqualTo(2);
    }

    @Test
    void damagedQuantityCompletesInspectionButDoesNotIncreaseAcceptedQuantity() throws Exception {
        Fixture fixture = packageFixture(2);
        mockMvc.perform(post("/api/domestic-packages/{packageId}/receipts", fixture.domesticPackage().getId())
                .with(actor("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(fixture.item().getId(), 1, 1, "idem-" + fixture.suffix(), "一件破损")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.packageStatus").value("EXCEPTION"))
            .andExpect(jsonPath("$.purchaseOrderStatus").value("RECEIVED"));

        assertThat(purchaseOrderItemRepository.findById(fixture.item().getId()).orElseThrow().getReceivedQuantity())
            .isEqualTo(1);
    }

    @Test
    void idempotentRetryReturnsTheOriginalReceipt() throws Exception {
        Fixture fixture = packageFixture(2);
        String request = receiptJson(fixture.item().getId(), 1, 0, "idem-" + fixture.suffix(), "首件");

        String first = mockMvc.perform(post("/api/domestic-packages/{packageId}/receipts", fixture.domesticPackage().getId())
                .with(actor("WAREHOUSE")).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/domestic-packages/{packageId}/receipts", fixture.domesticPackage().getId())
                .with(actor("WAREHOUSE")).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(receiptRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsReceiptThatExceedsRemainingQuantity() throws Exception {
        Fixture fixture = packageFixture(1);
        mockMvc.perform(post("/api/domestic-packages/{packageId}/receipts", fixture.domesticPackage().getId())
                .with(actor("WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(fixture.item().getId(), 2, 0, "idem-" + fixture.suffix(), "超量")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RECEIPT_QUANTITY_CONFLICT"));
    }

    @Test
    void packageListAndReceiptDetailAreReadableByAuthenticatedEmployees() throws Exception {
        Fixture fixture = packageFixture(2);
        String response = mockMvc.perform(post("/api/domestic-packages/{packageId}/receipts", fixture.domesticPackage().getId())
                .with(actor("WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(fixture.item().getId(), 1, 0, "idem-" + fixture.suffix(), "到货")))
            .andReturn().getResponse().getContentAsString();
        Long receiptId = Long.valueOf(response.replaceAll(".*\"receiptId\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/domestic-packages").with(actor("PACKING")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].trackingNumber").exists());
        mockMvc.perform(get("/api/goods-receipts/{receiptId}", receiptId).with(actor("OPERATIONS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].acceptedQuantity").value(1))
            .andExpect(jsonPath("$.inventoryPosted").value(false));
    }

    @Test
    void unusedPackageCanBeCanceled() throws Exception {
        Fixture fixture = packageFixture(1);
        mockMvc.perform(post("/api/domestic-packages/{packageId}/cancel", fixture.domesticPackage().getId())
                .with(actor("WAREHOUSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"物流单登记错误","version":0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELED"));
        assertThat(packageRepository.findById(fixture.domesticPackage().getId()).orElseThrow().getStatus())
            .isEqualTo(DomesticPackageStatus.CANCELED);
    }

    private String receiptJson(Long itemId, int accepted, int damaged, String idempotencyKey, String note) {
        return """
            {
              "idempotencyKey":"%s",
              "packageVersion":0,
              "items":[{
                "purchaseOrderItemId":%d,
                "acceptedQuantity":%d,
                "damagedQuantity":%d,
                "note":"%s"
              }],
              "unexpectedItems":[],
              "note":"接口测试"
            }
            """.formatted(idempotencyKey, itemId, accepted, damaged, note);
    }

    private Fixture packageFixture(int quantity) {
        Fixture fixture = purchaseFixture(quantity);
        DomesticPackage domesticPackage = packageRepository.save(new DomesticPackage(
            "PKG-TEST-" + fixture.suffix(), fixture.order(), "TRACK-" + fixture.suffix(),
            "SF_EXPRESS", warehouse
        ));
        return new Fixture(fixture.suffix(), fixture.order(), fixture.item(), domesticPackage);
    }

    private Fixture purchaseFixture(int quantity) {
        String suffix = suffix();
        Supplier supplier = supplierRepository.save(new Supplier(
            "SUP-RCV-" + suffix, "测试供应商", SupplierPlatform.MANUAL
        ));
        PurchaseOrder order = purchaseOrderRepository.save(new PurchaseOrder(
            "PO-RCV-" + suffix, supplier, "CNY", warehouse
        ));
        order.updateReceivingStatus(false, false);
        purchaseOrderRepository.save(order);
        PurchaseOrderItem item = purchaseOrderItemRepository.save(new PurchaseOrderItem(
            order, "测试商品", quantity, "CNY"
        ));
        return new Fixture(suffix, order, item, null);
    }

    private JwtRequestPostProcessor actor(String role) {
        return jwt().jwt(token -> token.subject(warehouse.getId().toString()).claim("roles", List.of(role)));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(
        String suffix, PurchaseOrder order, PurchaseOrderItem item, DomesticPackage domesticPackage
    ) {
    }
}
