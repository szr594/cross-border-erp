package com.szr.crosserp.integration.procurement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies only whitelisted procurement facts. Receipt counters remain locally owned. */
@Service
public class ProcurementSnapshotConsumer {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ProcurementSnapshotConsumer(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public void accept(byte[] body) throws java.io.IOException {
        JsonNode event = mapper.readTree(body);
        String eventId = text(event, "eventId", 36);
        UUID.fromString(eventId);
        if (!"procurement.snapshot.v1".equals(text(event, "eventType", 64))
            || !event.path("schemaVersion").isIntegralNumber() || event.path("schemaVersion").intValue() != 1) {
            throw invalid("Unsupported procurement event");
        }
        long id = positive(event, "aggregateId"), revision = positive(event, "aggregateVersion");
        JsonNode payload = event.path("payload");
        if (positive(payload, "purchaseOrderId") != id) throw invalid("Aggregate mismatch");
        String number = text(payload, "purchaseOrderNo", 64);
        long supplier = positive(payload, "supplierId");
        String supplierCode = text(payload, "supplierCode", 64);
        String supplierName = text(payload, "supplierName", 200);
        String status = PurchaseOrderStatus.valueOf(text(payload, "status", 32)).name();
        String currency = text(payload, "currency", 3);
        if (!currency.matches("[A-Z]{3}")) throw invalid("Invalid currency");
        JsonNode items = payload.path("items");
        if (!items.isArray() || items.isEmpty()) throw invalid("Missing items");
        Set<Long> itemIds = new HashSet<>(), allocationIds = new HashSet<>();
        for (JsonNode item : items) {
            if (!itemIds.add(positive(item, "purchaseOrderItemId"))) throw invalid("Duplicate item");
            text(item, "productName", 500);
            optionalText(item, "variantName", 500);
            optionalText(item, "supplierSku", 128);
            int purchased = quantity(item, "purchasedQuantity");
            var allocations = item.path("allocations");
            if (!allocations.isArray() || allocations.isEmpty()) throw invalid("Missing allocations");
            long allocated = 0;
            Set<Long> demands = new HashSet<>();
            for (var allocation : allocations) {
                if (!allocationIds.add(positive(allocation, "allocationId"))) throw invalid("Duplicate allocation");
                if (!demands.add(positive(allocation, "requestId"))) throw invalid("Duplicate demand");
                positive(allocation, "orderItemId");
                allocated += quantity(allocation, "quantity");
            }
            if (allocated != purchased) throw invalid("Allocation total mismatch");
        }
        if (jdbc.update("insert ignore into procurement_event_inbox(consumer_name,event_id,processed_at) values('legacy-procurement-sync',?,CURRENT_TIMESTAMP(6))", eventId) == 0) return;
        jdbc.update("insert ignore into purchase_supplier_projections(id,code,name) values(?,?,?)", supplier, supplierCode, supplierName);
        if (!supplierCode.equals(jdbc.queryForObject("select code from purchase_supplier_projections where id=? for update", String.class, supplier))) throw invalid("Supplier identity conflict");
        jdbc.update("insert ignore into purchase_business_projections(id,purchase_order_no,supplier_id,status,currency,authority) values(?,?,?,?,?,'PROCUREMENT')", id, number, supplier, status, currency);
        var current = jdbc.queryForMap("select * from purchase_business_projections where id=? for update", id);
        if (!number.equals(current.get("purchase_order_no")) || supplier != ((Number) current.get("supplier_id")).longValue()
            || !currency.equals(current.get("currency"))) throw invalid("Purchase identity conflict");
        boolean legacy = "LEGACY".equals(current.get("authority"));
        long previous = ((Number) current.get("source_version")).longValue();
        String hash = hash(payload.toString());
        if (!legacy && revision < previous) return;
        if (!legacy && revision == previous) {
            if (!hash.equals(current.get("snapshot_hash"))) throw invalid("Conflicting snapshot at same revision");
            return;
        }
        var historical = jdbc.queryForList("select id from purchase_item_projections where purchase_order_id=?", Long.class, id);
        if (!itemIds.containsAll(historical)) throw invalid("Cannot remove historical items");
        Set<Long> historicalAllocations = new HashSet<>(jdbc.queryForList("select a.id from purchase_allocation_projections a join purchase_item_projections i on i.id=a.purchase_order_item_id where i.purchase_order_id=?", Long.class, id));
        if (!allocationIds.containsAll(historicalAllocations)) throw invalid("Cannot remove historical allocations");
        for (var item : items) {
            long itemId = positive(item, "purchaseOrderItemId");
            int purchased = quantity(item, "purchasedQuantity");
            jdbc.update("insert ignore into purchase_item_projections(id,purchase_order_id,product_name,purchased_quantity,currency) values(?,?,?,?,?)", itemId, id, text(item, "productName", 500), purchased, currency);
            var existing = jdbc.queryForMap("select purchase_order_id,purchased_quantity from purchase_item_projections where id=? for update", itemId);
            if (((Number) existing.get("purchase_order_id")).longValue() != id || ((Number) existing.get("purchased_quantity")).intValue() != purchased) throw invalid("Item ownership or quantity conflict");
            jdbc.update("update purchase_item_projections set product_name=?,variant_name=?,supplier_sku=? where id=?", text(item, "productName", 500), optionalText(item, "variantName", 500), optionalText(item, "supplierSku", 128), itemId);
            for (var allocation : item.path("allocations")) {
                long request = positive(allocation, "requestId"), orderItem = positive(allocation, "orderItemId");
                var demand = jdbc.queryForMap("select order_item_id,requested_quantity from procurement_request_projections where request_id=?", request);
                int amount = quantity(allocation, "quantity");
                if (((Number) demand.get("order_item_id")).longValue() != orderItem || ((Number) demand.get("requested_quantity")).intValue() != amount) throw invalid("Demand projection mismatch");
                long allocationId = positive(allocation, "allocationId");
                jdbc.update("insert ignore into purchase_allocation_projections(id,purchase_order_item_id,procurement_request_id,allocated_quantity) values(?,?,?,?)", allocationId, itemId, request, amount);
                var existingAllocation = jdbc.queryForMap("select * from purchase_allocation_projections where id=? for update", allocationId);
                if (((Number) existingAllocation.get("purchase_order_item_id")).longValue() != itemId
                    || ((Number) existingAllocation.get("procurement_request_id")).longValue() != request
                    || ((Number) existingAllocation.get("allocated_quantity")).intValue() != amount) throw invalid("Allocation identity conflict");
            }
        }
        // A delayed ORDERED snapshot must never undo locally completed/partial receiving.
        String localStatus = String.valueOf(current.get("status"));
        if (Set.of("PARTIALLY_RECEIVED", "RECEIVED").contains(localStatus)) {
            if (Set.of("DRAFT", "CANCELED").contains(status)) throw invalid("Snapshot conflicts with receiving");
            status = localStatus;
        }
        jdbc.update("update purchase_business_projections set status=?,source_version=?,snapshot_hash=?,authority='PROCUREMENT',bridge_confirmed=true,version=version+1 where id=?", status, revision, hash, id);
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private long positive(JsonNode node, String key) {
        var value = node.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw invalid("Invalid " + key);
        return value.longValue();
    }
    private int quantity(JsonNode node, String key) { return Math.toIntExact(positive(node, key)); }
    private String text(JsonNode node, String key, int limit) {
        var value = node.path(key);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > limit) throw invalid("Invalid " + key);
        return value.asText();
    }
    private String optionalText(JsonNode node, String key, int limit) {
        return node.path(key).isMissingNode() || node.path(key).isNull() ? null : text(node, key, limit);
    }
    private IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }
}
