package com.szr.crosserp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderEventSource;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.OrderStatusEvent;
import com.szr.crosserp.order.model.ProcurementRequestStatus;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.OrderProcurementRequestRepository;
import com.szr.crosserp.order.repository.OrderStatusEventRepository;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.store.model.SalesChannel;
import com.szr.crosserp.store.model.SalesStore;
import com.szr.crosserp.store.repository.SalesChannelRepository;
import com.szr.crosserp.store.repository.SalesStoreRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderSchemaMigrationTest {

    private static final Set<String> ORDER_TABLES = Set.of(
        "SALES_CHANNELS",
        "SALES_STORES",
        "SALES_ORDERS",
        "SALES_ORDER_ITEMS",
        "ORDER_PROCUREMENT_REQUESTS",
        "ORDER_STATUS_EVENTS"
    );

    @Autowired private Flyway flyway;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private SalesChannelRepository channelRepository;
    @Autowired private SalesStoreRepository storeRepository;
    @Autowired private SalesOrderRepository orderRepository;
    @Autowired private SalesOrderItemRepository itemRepository;
    @Autowired private OrderProcurementRequestRepository requestRepository;
    @Autowired private OrderStatusEventRepository eventRepository;

    @Test
    void laterMigrationsKeepAllOrderTables() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");

        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet result = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toUpperCase());
            }
        }

        assertThat(tables).containsAll(ORDER_TABLES);
    }

    @Test
    void repositoriesPersistAndFindTheMinimumOrderChain() {
        String suffix = suffix();
        User user = userRepository.save(new User("operator-" + suffix, "演示运营", "not-used-in-this-test", false));
        SalesChannel channel = channelRepository.save(new SalesChannel("DEMO-" + suffix, "演示渠道"));
        SalesStore store = storeRepository.save(new SalesStore(channel, "STORE-" + suffix, "store-" + suffix, "演示店铺"));
        SalesOrder order = orderRepository.save(new SalesOrder("ERP-" + suffix, store, "EXT-" + suffix, ExternalOrderStatus.NEW));
        SalesOrderItem item = itemRepository.save(new SalesOrderItem(order, "ITEM-" + suffix, "演示商品", 2));
        OrderProcurementRequest request = requestRepository.save(
            new OrderProcurementRequest(item, 1, user, Instant.now())
        );
        OrderStatusEvent event = eventRepository.save(
            new OrderStatusEvent(order, "ORDER_IMPORTED", user, OrderEventSource.IMPORT, Instant.now())
        );

        assertThat(channelRepository.findByCode(channel.getCode())).contains(channel);
        assertThat(storeRepository.findByChannelIdAndExternalStoreId(channel.getId(), store.getExternalStoreId())).contains(store);
        assertThat(orderRepository.findByStoreIdAndExternalOrderId(store.getId(), order.getExternalOrderId())).contains(order);
        assertThat(itemRepository.findByOrderIdAndExternalItemId(order.getId(), item.getExternalItemId())).contains(item);
        assertThat(requestRepository.findByOrderItemIdAndStatus(item.getId(), ProcurementRequestStatus.REQUESTED)).containsExactly(request);
        assertThat(eventRepository.findByOrderIdOrderByOccurredAtDescIdDesc(order.getId())).containsExactly(event);
    }

    @Test
    void channelCodeMustBeUnique() {
        String code = "DUP-" + suffix();
        channelRepository.saveAndFlush(new SalesChannel(code, "渠道一"));

        assertThatThrownBy(() -> channelRepository.saveAndFlush(new SalesChannel(code, "渠道二")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storeExternalIdMustBeUniqueInsideChannel() {
        String suffix = suffix();
        SalesChannel channel = channelRepository.save(new SalesChannel("CH-" + suffix, "渠道"));
        storeRepository.saveAndFlush(new SalesStore(channel, "EXT", "S1-" + suffix, "店铺一"));

        assertThatThrownBy(() -> storeRepository.saveAndFlush(new SalesStore(channel, "EXT", "S2-" + suffix, "店铺二")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void externalOrderIdMustBeUniqueInsideStore() {
        String suffix = suffix();
        SalesStore store = createStore(suffix);
        orderRepository.saveAndFlush(new SalesOrder("ERP1-" + suffix, store, "EXT", ExternalOrderStatus.NEW));

        assertThatThrownBy(() -> orderRepository.saveAndFlush(
            new SalesOrder("ERP2-" + suffix, store, "EXT", ExternalOrderStatus.NEW)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void externalItemIdMustBeUniqueInsideOrder() {
        String suffix = suffix();
        SalesOrder order = createOrder(suffix);
        itemRepository.saveAndFlush(new SalesOrderItem(order, "ITEM", "商品一", 1));

        assertThatThrownBy(() -> itemRepository.saveAndFlush(new SalesOrderItem(order, "ITEM", "商品二", 1)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderItemQuantityMustBePositive() {
        SalesOrder order = createOrder(suffix());

        assertThatThrownBy(() -> itemRepository.saveAndFlush(new SalesOrderItem(order, "ZERO", "错误商品", 0)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void procurementRequestQuantityMustBePositive() {
        String suffix = suffix();
        User user = userRepository.save(new User("buyer-" + suffix, "演示采购", "not-used-in-this-test", false));
        SalesOrderItem item = itemRepository.save(new SalesOrderItem(createOrder(suffix), "ITEM", "演示商品", 1));

        assertThatThrownBy(() -> requestRepository.saveAndFlush(
            new OrderProcurementRequest(item, 0, user, Instant.now())
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void procurementRequestMustReferenceAnExistingUser() {
        SalesOrderItem item = itemRepository.saveAndFlush(
            new SalesOrderItem(createOrder(suffix()), "ITEM", "演示商品", 1)
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO order_procurement_requests "
                + "(order_item_id, requested_quantity, status, requested_by, requested_at, version) "
                + "VALUES (?, 1, 'REQUESTED', ?, CURRENT_TIMESTAMP, 0)",
            item.getId(),
            Long.MAX_VALUE
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private SalesStore createStore(String suffix) {
        SalesChannel channel = channelRepository.save(new SalesChannel("CH-" + suffix, "渠道"));
        return storeRepository.save(new SalesStore(channel, "STORE-" + suffix, "store-" + suffix, "店铺"));
    }

    private SalesOrder createOrder(String suffix) {
        return orderRepository.save(new SalesOrder(
            "ERP-" + suffix,
            createStore(suffix),
            "EXT-" + suffix,
            ExternalOrderStatus.NEW
        ));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
