package com.szr.crosserp.orderimport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.OrderEventSource;
import com.szr.crosserp.order.model.OrderStatusEvent;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import com.szr.crosserp.order.repository.OrderStatusEventRepository;
import com.szr.crosserp.order.repository.SalesOrderItemRepository;
import com.szr.crosserp.order.repository.SalesOrderRepository;
import com.szr.crosserp.orderimport.dto.OrderImportErrorResponse;
import com.szr.crosserp.orderimport.dto.OrderImportResponse;
import com.szr.crosserp.orderimport.model.OrderImportBatch;
import com.szr.crosserp.orderimport.model.OrderImportError;
import com.szr.crosserp.orderimport.model.OrderImportRow;
import com.szr.crosserp.orderimport.model.OrderImportRowStatus;
import com.szr.crosserp.orderimport.model.OrderImportStatus;
import com.szr.crosserp.orderimport.repository.OrderImportBatchRepository;
import com.szr.crosserp.orderimport.repository.OrderImportErrorRepository;
import com.szr.crosserp.orderimport.repository.OrderImportRowRepository;
import com.szr.crosserp.store.model.SalesStore;
import com.szr.crosserp.store.model.StoreStatus;
import com.szr.crosserp.store.repository.SalesStoreRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.model.UserStatus;
import com.szr.crosserp.user.repository.UserRepository;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrderImportService {
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ROWS = 10_000;
    private static final Duration PREVIEW_LIFETIME = Duration.ofHours(24);
    private static final List<String> HEADERS = List.of(
        "store_code", "external_order_id", "display_order_no", "external_status",
        "ordered_at", "requested_ship_at", "currency", "responsible_login",
        "customer_id", "customer_name", "recipient_name", "recipient_phone",
        "country_code", "province", "city", "district", "address_line1", "address_line2", "postal_code",
        "external_item_id", "external_product_id", "external_variant_id", "external_sku",
        "product_name", "variant_name", "image_url", "quantity", "unit_price_minor", "note"
    );
    private static final Set<String> REQUIRED = Set.of(
        "store_code", "external_order_id", "external_status", "ordered_at", "currency",
        "external_item_id", "product_name", "quantity"
    );

    private final OrderImportBatchRepository batchRepository;
    private final OrderImportRowRepository rowRepository;
    private final OrderImportErrorRepository errorRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final OrderStatusEventRepository eventRepository;
    private final SalesStoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrderImportService(
        OrderImportBatchRepository batchRepository, OrderImportRowRepository rowRepository,
        OrderImportErrorRepository errorRepository, SalesOrderRepository orderRepository,
        SalesOrderItemRepository itemRepository, OrderStatusEventRepository eventRepository,
        SalesStoreRepository storeRepository, UserRepository userRepository, ObjectMapper objectMapper, Clock clock
    ) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.errorRepository = errorRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.eventRepository = eventRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public OrderImportResponse preview(MultipartFile file, String subject, List<String> roles) {
        User actor = requireOwner(subject, roles);
        byte[] bytes = readFile(file);
        String content = decodeUtf8(bytes);
        Instant now = clock.instant();
        OrderImportBatch batch = batchRepository.save(new OrderImportBatch(
            UUID.randomUUID().toString(), safeFileName(file.getOriginalFilename()), sha256(bytes), actor,
            now, now.plus(PREVIEW_LIFETIME)
        ));

        List<PendingError> errors = new ArrayList<>();
        List<OrderImportCsvRow> rows = parse(content, errors);
        if (rows.size() > MAX_ROWS) {
            errors.add(new PendingError(0, null, "TOO_MANY_ROWS", "CSV 最多允许 10000 行数据"));
            rows = rows.subList(0, MAX_ROWS);
        }
        validateRows(rows, errors);
        validateGroups(rows, errors);

        Set<Integer> invalidRows = errors.stream().map(PendingError::rowNumber).collect(Collectors.toSet());
        for (OrderImportCsvRow row : rows) {
            rowRepository.save(new OrderImportRow(
                batch, row.rowNumber(), row.orderKey(), toJson(row), sha256(toJson(row).getBytes(StandardCharsets.UTF_8)),
                invalidRows.contains(row.rowNumber()) ? OrderImportRowStatus.INVALID : OrderImportRowStatus.VALID
            ));
        }
        errors.forEach(error -> errorRepository.save(new OrderImportError(
            batch, error.rowNumber(), error.field(), error.code(), error.message()
        )));

        Map<String, List<OrderImportCsvRow>> groups = group(rows);
        int skipped = 0;
        for (List<OrderImportCsvRow> group : groups.values()) {
            OrderImportCsvRow first = group.get(0);
            SalesStore store = storeRepository.findByCode(first.storeCode()).orElse(null);
            if (store != null) {
                SalesOrder existing = orderRepository.findByStoreIdAndExternalOrderId(store.getId(), first.externalOrderId()).orElse(null);
                if (existing != null && equivalent(existing, group)) skipped++;
            }
        }
        int creatable = Math.max(0, groups.size() - skipped);
        batch.finishValidation(rows.size(), groups.size(), rows.size(), creatable, skipped, errors.size());
        return response(batch);
    }

    @Transactional(readOnly = true)
    public OrderImportResponse get(String importId, String subject, List<String> roles) {
        requireOwner(subject, roles);
        return response(findBatch(importId));
    }

    @Transactional(readOnly = true)
    public com.szr.crosserp.orderimport.dto.OrderImportErrorPageResponse errors(
        String importId, int page, int size, String subject, List<String> roles
    ) {
        requireOwner(subject, roles);
        findBatch(importId);
        if (page < 1 || size < 1 || size > 100) {
            throw badRequest("分页参数无效");
        }
        Page<OrderImportError> result = errorRepository.findByBatchId(
            importId, PageRequest.of(page - 1, size, Sort.by("rowNumber").ascending().and(Sort.by("id").ascending()))
        );
        return new com.szr.crosserp.orderimport.dto.OrderImportErrorPageResponse(
            result.getContent().stream().map(error -> new OrderImportErrorResponse(
                error.getRowNumber(), error.getFieldName(), error.getErrorCode(), error.getErrorMessage()
            )).toList(), page, size, result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional
    public OrderImportResponse confirm(String importId, String subject, List<String> roles) {
        User actor = requireOwner(subject, roles);
        OrderImportBatch batch = findBatch(importId);
        if (batch.getStatus() == OrderImportStatus.COMPLETED) return response(batch);
        if (batch.getStatus() != OrderImportStatus.VALIDATED || batch.getErrorCount() != 0) {
            throw conflict("ORDER_IMPORT_NOT_CONFIRMABLE", "当前导入批次不能确认");
        }
        Instant now = clock.instant();
        if (!batch.getExpiresAt().isAfter(now)) {
            throw conflict("ORDER_IMPORT_EXPIRED", "预检结果已过期，请重新上传");
        }
        List<OrderImportCsvRow> rows = rowRepository.findByBatchIdOrderByRowNumberAsc(importId).stream()
            .map(row -> fromJson(row.getNormalizedPayload())).toList();
        batch.startImport(now);
        int ordinal = 0;
        for (List<OrderImportCsvRow> group : group(rows).values()) {
            OrderImportCsvRow first = group.get(0);
            SalesStore store = storeRepository.findByCode(first.storeCode())
                .orElseThrow(() -> conflict("ORDER_IMPORT_SOURCE_CHANGED", "店铺信息已变化，请重新预检"));
            SalesOrder existing = orderRepository.findByStoreIdAndExternalOrderId(store.getId(), first.externalOrderId()).orElse(null);
            if (existing != null) {
                if (equivalent(existing, group)) continue;
                throw conflict("EXISTING_ORDER_CONFLICT", "已有订单内容与导入文件不一致");
            }
            User responsible = nullable(first.responsibleLogin()) == null ? null : userRepository
                .findByLoginNameIgnoreCase(first.responsibleLogin())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> conflict("ORDER_IMPORT_SOURCE_CHANGED", "负责人信息已变化，请重新预检"));
            SalesOrder order = SalesOrder.imported(
                orderNumber(batch.getId(), ++ordinal), store, first.externalOrderId(), nullable(first.displayOrderNo()),
                ExternalOrderStatus.valueOf(first.externalStatus()), first.externalStatus(), responsible,
                first.currency(), Instant.parse(first.orderedAt()), parseInstant(first.requestedShipAt()),
                nullable(first.customerId()), nullable(first.customerName()), nullable(first.recipientName()),
                nullable(first.recipientPhone()), nullable(first.countryCode()), nullable(first.province()),
                nullable(first.city()), nullable(first.district()), nullable(first.addressLine1()),
                nullable(first.addressLine2()), nullable(first.postalCode()), nullable(first.note()), now
            );
            orderRepository.save(order);
            for (OrderImportCsvRow row : group) {
                itemRepository.save(SalesOrderItem.imported(
                    order, row.externalItemId(), nullable(row.externalProductId()), nullable(row.externalVariantId()),
                    nullable(row.externalSku()), row.productName(), nullable(row.variantName()), nullable(row.imageUrl()),
                    Integer.parseInt(row.quantity()), parseLong(row.unitPriceMinor()), row.currency(), nullable(row.note()), now
                ));
            }
            eventRepository.save(new OrderStatusEvent(
                order, "ORDER_IMPORTED", null, order.getExternalStatus().name(), actor,
                OrderEventSource.IMPORT, "CSV 批次 " + batch.getId(), now
            ));
        }
        orderRepository.flush();
        batch.complete(clock.instant());
        return response(batch);
    }

    private List<OrderImportCsvRow> parse(String content, List<PendingError> errors) {
        String normalized = content.startsWith("\uFEFF") ? content.substring(1) : content;
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true).setTrim(true).build().parse(new StringReader(normalized))) {
            if (!parser.getHeaderNames().equals(HEADERS)) {
                errors.add(new PendingError(0, null, "INVALID_HEADER", "CSV 表头必须与模板 v1 完全一致"));
                return List.of();
            }
            List<OrderImportCsvRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) rows.add(toRow(record));
            if (rows.isEmpty()) errors.add(new PendingError(0, null, "EMPTY_FILE", "CSV 没有数据行"));
            return rows;
        } catch (IOException | IllegalArgumentException exception) {
            errors.add(new PendingError(0, null, "CSV_PARSE_ERROR", "CSV 格式无法解析"));
            return List.of();
        }
    }

    private OrderImportCsvRow toRow(CSVRecord record) {
        int row = Math.toIntExact(record.getRecordNumber() + 1);
        List<String> values = HEADERS.stream().map(header -> record.get(header).trim()).toList();
        return new OrderImportCsvRow(row, values.get(0), values.get(1), values.get(2), values.get(3), values.get(4),
            values.get(5), values.get(6).toUpperCase(), values.get(7), values.get(8), values.get(9), values.get(10),
            values.get(11), values.get(12).toUpperCase(), values.get(13), values.get(14), values.get(15), values.get(16),
            values.get(17), values.get(18), values.get(19), values.get(20), values.get(21), values.get(22), values.get(23),
            values.get(24), values.get(25), values.get(26), values.get(27), values.get(28));
    }

    private void validateRows(List<OrderImportCsvRow> rows, List<PendingError> errors) {
        Map<String, SalesStore> stores = new HashMap<>();
        Map<String, User> users = new HashMap<>();
        for (OrderImportCsvRow row : rows) {
            Map<String, String> values = values(row);
            REQUIRED.forEach(field -> {
                if (values.get(field) == null || values.get(field).isBlank()) {
                    errors.add(new PendingError(row.rowNumber(), field, "MISSING_REQUIRED_FIELD", field + " 为必填项"));
                }
            });
            enumValue(row, errors);
            instantValue(row.rowNumber(), "ordered_at", row.orderedAt(), true, errors);
            instantValue(row.rowNumber(), "requested_ship_at", row.requestedShipAt(), false, errors);
            if (!row.currency().matches("[A-Z]{3}")) error(errors, row, "currency", "INVALID_CURRENCY", "币种必须是三个大写字母");
            if (!row.countryCode().isBlank() && !row.countryCode().matches("[A-Z]{2}")) {
                error(errors, row, "country_code", "INVALID_COUNTRY_CODE", "国家代码必须是两个大写字母");
            }
            positiveInt(row, errors);
            nonNegativeLong(row, errors);
            SalesStore store = stores.computeIfAbsent(row.storeCode(), code -> storeRepository.findByCode(code).orElse(null));
            if (!row.storeCode().isBlank() && store == null) error(errors, row, "store_code", "STORE_NOT_FOUND", "店铺编码不存在");
            else if (store != null && store.getStatus() != StoreStatus.ACTIVE) error(errors, row, "store_code", "STORE_INACTIVE", "店铺已停用");
            if (!row.responsibleLogin().isBlank()) {
                User user = users.computeIfAbsent(row.responsibleLogin().toLowerCase(),
                    login -> userRepository.findByLoginNameIgnoreCase(login).orElse(null));
                if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                    error(errors, row, "responsible_login", "RESPONSIBLE_USER_NOT_FOUND", "负责人不存在或已停用");
                }
            }
        }
    }

    private void validateGroups(List<OrderImportCsvRow> rows, List<PendingError> errors) {
        for (List<OrderImportCsvRow> group : group(rows).values()) {
            OrderImportCsvRow first = group.get(0);
            String header = headerFingerprint(first);
            Set<String> items = new HashSet<>();
            for (OrderImportCsvRow row : group) {
                if (!header.equals(headerFingerprint(row))) {
                    error(errors, row, null, "INCONSISTENT_ORDER_FIELD", "同一订单的订单级字段必须一致");
                }
                if (!items.add(row.externalItemId())) {
                    error(errors, row, "external_item_id", "DUPLICATE_ITEM_IN_FILE", "同一订单的商品行不能重复");
                }
            }
            SalesStore store = storeRepository.findByCode(first.storeCode()).orElse(null);
            if (store != null) {
                SalesOrder existing = orderRepository.findByStoreIdAndExternalOrderId(store.getId(), first.externalOrderId()).orElse(null);
                if (existing != null && !equivalent(existing, group)) {
                    error(errors, first, "external_order_id", "EXISTING_ORDER_CONFLICT", "已有订单内容与导入文件不一致");
                }
            }
        }
    }

    private boolean equivalent(SalesOrder order, List<OrderImportCsvRow> rows) {
        OrderImportCsvRow first = rows.get(0);
        if (!Objects.equals(order.getDisplayOrderNo(), nullable(first.displayOrderNo()))
            || order.getExternalStatus() != safeStatus(first.externalStatus())
            || !Objects.equals(order.getCurrency(), first.currency())
            || !Objects.equals(order.getOrderedAt(), parseInstant(first.orderedAt()))
            || !Objects.equals(order.getRequestedShipAt(), parseInstant(first.requestedShipAt()))
            || !Objects.equals(order.getCustomerId(), nullable(first.customerId()))
            || !Objects.equals(order.getCustomerName(), nullable(first.customerName()))
            || !Objects.equals(order.getRecipientName(), nullable(first.recipientName()))
            || !Objects.equals(order.getRecipientPhone(), nullable(first.recipientPhone()))
            || !Objects.equals(order.getCountryCode(), nullable(first.countryCode()))
            || !Objects.equals(order.getProvince(), nullable(first.province()))
            || !Objects.equals(order.getCity(), nullable(first.city()))
            || !Objects.equals(order.getDistrict(), nullable(first.district()))
            || !Objects.equals(order.getAddressLine1(), nullable(first.addressLine1()))
            || !Objects.equals(order.getAddressLine2(), nullable(first.addressLine2()))
            || !Objects.equals(order.getPostalCode(), nullable(first.postalCode()))
            || !Objects.equals(order.getNote(), nullable(first.note()))) return false;
        String login = order.getResponsibleUser() == null ? null : order.getResponsibleUser().getLoginName();
        if (!Objects.equals(login == null ? null : login.toLowerCase(), lowerNullable(first.responsibleLogin()))) return false;
        Map<String, SalesOrderItem> existing = itemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
            .collect(Collectors.toMap(SalesOrderItem::getExternalItemId, Function.identity()));
        if (existing.size() != rows.size()) return false;
        for (OrderImportCsvRow row : rows) {
            SalesOrderItem item = existing.get(row.externalItemId());
            if (item == null || item.getQuantity() != parseInt(row.quantity())
                || !Objects.equals(item.getExternalProductId(), nullable(row.externalProductId()))
                || !Objects.equals(item.getExternalVariantId(), nullable(row.externalVariantId()))
                || !Objects.equals(item.getExternalSku(), nullable(row.externalSku()))
                || !Objects.equals(item.getProductName(), row.productName())
                || !Objects.equals(item.getVariantName(), nullable(row.variantName()))
                || !Objects.equals(item.getImageUrl(), nullable(row.imageUrl()))
                || !Objects.equals(item.getUnitPriceMinor(), parseLong(row.unitPriceMinor()))
                || !Objects.equals(item.getCurrency(), row.currency())
                || !Objects.equals(item.getNote(), nullable(row.note()))) return false;
        }
        return true;
    }

    private Map<String, List<OrderImportCsvRow>> group(List<OrderImportCsvRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(
            OrderImportCsvRow::orderKey, LinkedHashMap::new, Collectors.toList()
        ));
    }

    private Map<String, String> values(OrderImportCsvRow row) {
        return Map.ofEntries(
            Map.entry("store_code", row.storeCode()), Map.entry("external_order_id", row.externalOrderId()),
            Map.entry("external_status", row.externalStatus()), Map.entry("ordered_at", row.orderedAt()),
            Map.entry("currency", row.currency()), Map.entry("external_item_id", row.externalItemId()),
            Map.entry("product_name", row.productName()), Map.entry("quantity", row.quantity())
        );
    }

    private String headerFingerprint(OrderImportCsvRow row) {
        return String.join("\u001f", row.storeCode(), row.externalOrderId(), row.displayOrderNo(), row.externalStatus(),
            row.orderedAt(), row.requestedShipAt(), row.currency(), row.responsibleLogin(), row.customerId(),
            row.customerName(), row.recipientName(), row.recipientPhone(), row.countryCode(), row.province(), row.city(),
            row.district(), row.addressLine1(), row.addressLine2(), row.postalCode(), row.note());
    }

    private void enumValue(OrderImportCsvRow row, List<PendingError> errors) {
        if (safeStatus(row.externalStatus()) == null) {
            error(errors, row, "external_status", "INVALID_EXTERNAL_STATUS", "订单状态不在允许范围内");
        }
    }

    private void positiveInt(OrderImportCsvRow row, List<PendingError> errors) {
        try {
            if (Integer.parseInt(row.quantity()) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            error(errors, row, "quantity", "INVALID_QUANTITY", "数量必须是正整数");
        }
    }

    private void nonNegativeLong(OrderImportCsvRow row, List<PendingError> errors) {
        if (row.unitPriceMinor().isBlank()) return;
        try {
            if (Long.parseLong(row.unitPriceMinor()) < 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            error(errors, row, "unit_price_minor", "INVALID_UNIT_PRICE", "单价必须是非负整数");
        }
    }

    private void instantValue(int row, String field, String value, boolean required, List<PendingError> errors) {
        if (value.isBlank() && !required) return;
        try { Instant.parse(value); }
        catch (DateTimeParseException exception) {
            errors.add(new PendingError(row, field, "INVALID_DATE_TIME", field + " 必须是带时区的 ISO 8601 时间"));
        }
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("请选择非空 CSV 文件");
        if (file.getSize() > MAX_FILE_BYTES) throw badRequest("CSV 文件不能超过 5 MB");
        try { return file.getBytes(); }
        catch (IOException exception) { throw badRequest("CSV 文件读取失败"); }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw badRequest("CSV 必须使用 UTF-8 编码");
        }
    }

    private User requireOwner(String subject, List<String> roles) {
        if (roles == null || !roles.contains("OWNER")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORDER_IMPORT_FORBIDDEN", "只有老板可以操作订单导入");
        }
        try {
            return userRepository.findById(Long.valueOf(subject))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效"));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效");
        }
    }

    private OrderImportBatch findBatch(String id) {
        return batchRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_IMPORT_NOT_FOUND", "导入批次不存在"));
    }

    private OrderImportResponse response(OrderImportBatch batch) {
        return new OrderImportResponse(
            batch.getId(), batch.getStatus().name(), batch.getOriginalFileName(), batch.getTotalRows(),
            batch.getOrderCount(), batch.getItemCount(), batch.getCreatableOrderCount(), batch.getSkippedOrderCount(),
            batch.getErrorCount(), batch.getCreatedAt(), batch.getExpiresAt(), batch.getConfirmedAt(), batch.getCompletedAt()
        );
    }

    private String toJson(OrderImportCsvRow row) {
        try { return objectMapper.writeValueAsString(row); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize validated CSV row", exception); }
    }

    private OrderImportCsvRow fromJson(String value) {
        try { return objectMapper.readValue(value, OrderImportCsvRow.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot read validated CSV row", exception); }
    }

    private String safeFileName(String name) {
        String value = name == null ? "orders.csv" : name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isBlank()) value = "orders.csv";
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    private String orderNumber(String batchId, int ordinal) {
        return "CSV-" + batchId.replace("-", "").substring(0, 16).toUpperCase() + "-" + String.format("%05d", ordinal);
    }

    private String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private ExternalOrderStatus safeStatus(String value) {
        try { return ExternalOrderStatus.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException exception) { return null; }
    }

    private Instant parseInstant(String value) { return nullable(value) == null ? null : Instant.parse(value); }
    private int parseInt(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException exception) { return -1; } }
    private Long parseLong(String value) { try { return nullable(value) == null ? null : Long.valueOf(value); } catch (NumberFormatException exception) { return null; } }
    private String nullable(String value) { return value == null || value.isBlank() ? null : value; }
    private String lowerNullable(String value) { String result = nullable(value); return result == null ? null : result.toLowerCase(); }

    private void error(List<PendingError> errors, OrderImportCsvRow row, String field, String code, String message) {
        errors.add(new PendingError(row.rowNumber(), field, code, message));
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ORDER_IMPORT_INVALID", message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private record PendingError(int rowNumber, String field, String code, String message) {}
}
