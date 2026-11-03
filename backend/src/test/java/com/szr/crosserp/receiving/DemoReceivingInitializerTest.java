package com.szr.crosserp.receiving;

import static org.assertj.core.api.Assertions.assertThat;

import com.szr.crosserp.order.config.DemoOrderInitializer;
import com.szr.crosserp.procurement.config.DemoPurchaseOrderInitializer;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.receiving.config.DemoReceivingInitializer;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.service.ReceivingCommandService;
import com.szr.crosserp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoReceivingInitializerTest {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderItemRepository purchaseItemRepository;
    @Autowired DomesticPackageRepository packageRepository;
    @Autowired ReceivingCommandService commandService;

    @Test
    void createsTraceableReceivingDemoIdempotently() {
        jdbcTemplate.update("""
            INSERT INTO app_users(login_name, display_name, password_hash, status)
            SELECT 'owner', '演示老板', 'test-only-password-hash', 'ACTIVE'
            WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE login_name = 'owner')
            """);
        new DemoOrderInitializer(jdbcTemplate).run(null);
        new DemoPurchaseOrderInitializer(jdbcTemplate).run(null);
        DemoReceivingInitializer initializer = new DemoReceivingInitializer(
            userRepository, purchaseOrderRepository, purchaseItemRepository, packageRepository, commandService
        );

        initializer.run(null);
        initializer.run(null);

        assertThat(count("SELECT COUNT(*) FROM domestic_packages WHERE tracking_number = 'DEMO-SF-20260001'"))
            .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM goods_receipts WHERE idempotency_key = 'demo-receipt-20260001'"))
            .isEqualTo(1);
        assertThat(count("""
            SELECT COUNT(*) FROM goods_receipt_items item
            JOIN goods_receipts receipt ON receipt.id = item.goods_receipt_id
            WHERE receipt.idempotency_key = 'demo-receipt-20260001'
            """))
            .isEqualTo(1);
        assertThat(count("""
            SELECT COUNT(*) FROM unexpected_receipt_items item
            JOIN goods_receipts receipt ON receipt.id = item.goods_receipt_id
            WHERE receipt.idempotency_key = 'demo-receipt-20260001'
            """))
            .isEqualTo(1);
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
