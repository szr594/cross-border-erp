package com.szr.crosserp.receiving.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.procurement.repository.PurchaseOrderRepository;
import com.szr.crosserp.receiving.dto.CancelDomesticPackageRequest;
import com.szr.crosserp.receiving.dto.ConfirmGoodsReceiptRequest;
import com.szr.crosserp.receiving.dto.ConfirmGoodsReceiptResponse;
import com.szr.crosserp.receiving.dto.ConfirmReceiptItemCommand;
import com.szr.crosserp.receiving.dto.CreateDomesticPackageRequest;
import com.szr.crosserp.receiving.dto.DomesticPackageCommandResponse;
import com.szr.crosserp.receiving.dto.UnexpectedReceiptItemCommand;
import com.szr.crosserp.receiving.model.DomesticPackage;
import com.szr.crosserp.receiving.model.DomesticPackageStatus;
import com.szr.crosserp.receiving.model.GoodsReceipt;
import com.szr.crosserp.receiving.model.GoodsReceiptItem;
import com.szr.crosserp.receiving.model.ReceivingEvent;
import com.szr.crosserp.receiving.model.UnexpectedReceiptItem;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptItemRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.receiving.repository.ReceivingEventRepository;
import com.szr.crosserp.receiving.repository.UnexpectedReceiptItemRepository;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivingCommandService {
    private static final Set<String> WRITE_ROLES = Set.of("OWNER", "WAREHOUSE");
    private static final Set<PurchaseOrderStatus> RECEIVABLE_STATUSES = Set.of(
        PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED
    );
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final DomesticPackageRepository packageRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseItemRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptItemRepository receiptItemRepository;
    private final UnexpectedReceiptItemRepository unexpectedItemRepository;
    private final ReceivingEventRepository eventRepository;
    private final UserRepository userRepository;

    public ReceivingCommandService(
        DomesticPackageRepository packageRepository,
        PurchaseOrderRepository purchaseOrderRepository,
        PurchaseOrderItemRepository purchaseItemRepository,
        GoodsReceiptRepository receiptRepository,
        GoodsReceiptItemRepository receiptItemRepository,
        UnexpectedReceiptItemRepository unexpectedItemRepository,
        ReceivingEventRepository eventRepository,
        UserRepository userRepository
    ) {
        this.packageRepository = packageRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.unexpectedItemRepository = unexpectedItemRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DomesticPackageCommandResponse register(
        CreateDomesticPackageRequest command, String subject, List<String> roles
    ) {
        requireWriteRole(roles);
        User actor = requireActor(subject);
        PurchaseOrder order = purchaseOrderRepository.findById(command.purchaseOrderId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PURCHASE_ORDER_NOT_FOUND", "采购单不存在"));
        if (!RECEIVABLE_STATUSES.contains(order.getStatus())) {
            throw notReceivable();
        }
        String carrier = command.carrier().trim().toUpperCase(Locale.ROOT);
        String trackingNumber = command.trackingNumber().trim();
        packageRepository.findByCarrierAndTrackingNumber(carrier, trackingNumber).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "TRACKING_NUMBER_EXISTS", "物流单号已经登记");
        });
        Instant now = Instant.now();
        DomesticPackage domesticPackage = packageRepository.saveAndFlush(new DomesticPackage(
            packageNo(now), order, trackingNumber, carrier, command.expectedAt(), normalize(command.note()), actor
        ));
        eventRepository.save(new ReceivingEvent(
            domesticPackage, null, "PACKAGE_REGISTERED", actor, normalize(command.note()), now
        ));
        return packageResponse(domesticPackage);
    }

    @Transactional
    public ConfirmGoodsReceiptResponse confirm(
        Long packageId, ConfirmGoodsReceiptRequest command, String subject, List<String> roles
    ) {
        requireWriteRole(roles);
        User actor = requireActor(subject);
        validateReceipt(command);
        String requestHash = requestHash(packageId, command);
        GoodsReceipt existing = receiptRepository.findByIdempotencyKey(command.idempotencyKey().trim()).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw conflict("RECEIPT_IDEMPOTENCY_CONFLICT", "相同幂等键对应不同收货内容");
            }
            return receiptResponse(existing);
        }

        DomesticPackage domesticPackage = packageRepository.findByIdForUpdate(packageId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOMESTIC_PACKAGE_NOT_FOUND", "国内包裹不存在"));
        if (domesticPackage.getVersion() != command.packageVersion()) {
            throw conflict("RECEIPT_VERSION_CONFLICT", "包裹已被其他人更新，请刷新后重试");
        }
        if (domesticPackage.getStatus() == DomesticPackageStatus.CANCELED) {
            throw notReceivable();
        }
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(domesticPackage.getPurchaseOrder().getId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PURCHASE_ORDER_NOT_FOUND", "采购单不存在"));
        if (!RECEIVABLE_STATUSES.contains(order.getStatus())) {
            throw notReceivable();
        }

        List<PurchaseOrderItem> orderItems = purchaseItemRepository.findByPurchaseOrderIdOrderByIdAsc(order.getId());
        List<Long> orderItemIds = orderItems.stream().map(PurchaseOrderItem::getId).sorted().toList();
        List<PurchaseOrderItem> lockedItems = purchaseItemRepository.findAllForUpdateByIdIn(orderItemIds);
        Map<Long, PurchaseOrderItem> itemsById = new HashMap<>();
        lockedItems.forEach(item -> itemsById.put(item.getId(), item));
        Map<Long, Integer> previousDamaged = damagedByItem(orderItemIds);

        for (ConfirmReceiptItemCommand itemCommand : command.items()) {
            PurchaseOrderItem item = itemsById.get(itemCommand.purchaseOrderItemId());
            if (item == null) {
                throw new ApiException(
                    HttpStatus.NOT_FOUND, "PURCHASE_ORDER_ITEM_NOT_FOUND", "采购商品不存在或不属于当前采购单"
                );
            }
            int inspected = item.getReceivedQuantity() + previousDamaged.getOrDefault(item.getId(), 0);
            int incoming = itemCommand.acceptedQuantity() + itemCommand.damagedQuantity();
            if (inspected + incoming > item.getPurchasedQuantity()) {
                throw conflict("RECEIPT_QUANTITY_CONFLICT", "累计验货数量超过采购数量");
            }
            item.receiveAccepted(itemCommand.acceptedQuantity());
        }

        Instant now = Instant.now();
        GoodsReceipt receipt = receiptRepository.saveAndFlush(new GoodsReceipt(
            receiptNo(now), domesticPackage, actor, now, command.idempotencyKey().trim(),
            requestHash, normalize(command.note())
        ));
        List<GoodsReceiptItem> receiptItems = command.items().stream().map(item -> new GoodsReceiptItem(
            receipt, itemsById.get(item.purchaseOrderItemId()), item.acceptedQuantity(),
            item.damagedQuantity(), normalize(item.note())
        )).toList();
        receiptItemRepository.saveAll(receiptItems);
        List<UnexpectedReceiptItem> unexpectedItems = command.unexpectedItems().stream().map(item ->
            new UnexpectedReceiptItem(
                receipt, item.description().trim(), normalize(item.supplierSku()), item.quantity(), normalize(item.note())
            )
        ).toList();
        unexpectedItemRepository.saveAll(unexpectedItems);
        purchaseItemRepository.saveAllAndFlush(lockedItems);
        receiptItemRepository.flush();

        Map<Long, Integer> allDamaged = damagedByItem(orderItemIds);
        boolean anyInspected = false;
        boolean allInspected = true;
        for (PurchaseOrderItem item : lockedItems) {
            int inspected = item.getReceivedQuantity() + allDamaged.getOrDefault(item.getId(), 0);
            anyInspected |= inspected > 0;
            allInspected &= inspected == item.getPurchasedQuantity();
        }
        order.updateReceivingStatus(anyInspected, allInspected);
        boolean hasException = domesticPackage.getStatus() == DomesticPackageStatus.EXCEPTION
            || receiptItems.stream().anyMatch(item -> item.getDamagedQuantity() > 0)
            || !unexpectedItems.isEmpty();
        domesticPackage.recordReceipt(now, allInspected, hasException);
        purchaseOrderRepository.save(order);
        packageRepository.save(domesticPackage);
        eventRepository.save(new ReceivingEvent(
            domesticPackage, receipt, "RECEIPT_CONFIRMED", actor, normalize(command.note()), now
        ));
        if (receiptItems.stream().anyMatch(item -> item.getDamagedQuantity() > 0)) {
            eventRepository.save(new ReceivingEvent(
                domesticPackage, receipt, "DAMAGE_RECORDED", actor, "本批次包含破损品", now
            ));
        }
        if (!unexpectedItems.isEmpty()) {
            eventRepository.save(new ReceivingEvent(
                domesticPackage, receipt, "UNEXPECTED_ITEM_RECORDED", actor, "本批次包含错货", now
            ));
        }
        if (allInspected) {
            eventRepository.save(new ReceivingEvent(
                domesticPackage, receipt, "PACKAGE_COMPLETED", actor, "采购商品已全部验完", now
            ));
        }
        packageRepository.flush();
        return receiptResponse(receipt);
    }

    @Transactional
    public DomesticPackageCommandResponse cancel(
        Long packageId, CancelDomesticPackageRequest command, String subject, List<String> roles
    ) {
        requireWriteRole(roles);
        User actor = requireActor(subject);
        DomesticPackage domesticPackage = packageRepository.findByIdForUpdate(packageId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOMESTIC_PACKAGE_NOT_FOUND", "国内包裹不存在"));
        if (domesticPackage.getVersion() != command.version()) {
            throw conflict("RECEIPT_VERSION_CONFLICT", "包裹已被其他人更新，请刷新后重试");
        }
        if (receiptRepository.existsByDomesticPackageId(packageId)) {
            throw conflict("PACKAGE_ALREADY_RECEIVED", "已有确认收货记录，不能取消包裹");
        }
        if (domesticPackage.getStatus() == DomesticPackageStatus.CANCELED) {
            throw conflict("PACKAGE_ALREADY_CANCELED", "包裹已经取消");
        }
        domesticPackage.cancel();
        Instant now = Instant.now();
        eventRepository.save(new ReceivingEvent(
            domesticPackage, null, "PACKAGE_CANCELED", actor, command.reason().trim(), now
        ));
        packageRepository.flush();
        return packageResponse(domesticPackage);
    }

    private Map<Long, Integer> damagedByItem(List<Long> itemIds) {
        Map<Long, Integer> damaged = new HashMap<>();
        if (!itemIds.isEmpty()) {
            receiptItemRepository.findByPurchaseOrderItemIdInOrderByIdAsc(itemIds).forEach(item ->
                damaged.merge(item.getPurchaseOrderItem().getId(), item.getDamagedQuantity(), Integer::sum)
            );
        }
        return damaged;
    }

    private void validateReceipt(ConfirmGoodsReceiptRequest command) {
        if (command.items().isEmpty() && command.unexpectedItems().isEmpty()) {
            throw invalid("至少需要一条收货或错货明细");
        }
        Set<Long> ids = new HashSet<>();
        for (ConfirmReceiptItemCommand item : command.items()) {
            if (!ids.add(item.purchaseOrderItemId())) {
                throw invalid("同一采购商品不能重复提交");
            }
            if (item.acceptedQuantity() + item.damagedQuantity() <= 0) {
                throw invalid("合格和破损数量合计必须大于0");
            }
            if (item.damagedQuantity() > 0 && (item.note() == null || item.note().isBlank())) {
                throw invalid("记录破损品时必须填写说明");
            }
        }
    }

    private String requestHash(Long packageId, ConfirmGoodsReceiptRequest command) {
        List<String> parts = new ArrayList<>();
        command.items().stream().sorted(Comparator.comparing(ConfirmReceiptItemCommand::purchaseOrderItemId))
            .forEach(item -> parts.add(String.join("|",
                "I", item.purchaseOrderItemId().toString(), String.valueOf(item.acceptedQuantity()),
                String.valueOf(item.damagedQuantity()), normalizeForHash(item.note())
            )));
        command.unexpectedItems().stream().sorted(Comparator.comparing(UnexpectedReceiptItemCommand::description))
            .forEach(item -> parts.add(String.join("|",
                "U", item.description().trim(), normalizeForHash(item.supplierSku()),
                String.valueOf(item.quantity()), normalizeForHash(item.note())
            )));
        String canonical = packageId + "\n" + normalizeForHash(command.note()) + "\n" + String.join("\n", parts);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                canonical.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ConfirmGoodsReceiptResponse receiptResponse(GoodsReceipt receipt) {
        return new ConfirmGoodsReceiptResponse(
            receipt.getId(), receipt.getReceiptNo(), receipt.getDomesticPackage().getId(),
            receipt.getDomesticPackage().getStatus(), receipt.getDomesticPackage().getPurchaseOrder().getStatus(), false
        );
    }

    private DomesticPackageCommandResponse packageResponse(DomesticPackage domesticPackage) {
        return new DomesticPackageCommandResponse(
            domesticPackage.getId(), domesticPackage.getPackageNo(), domesticPackage.getTrackingNumber(),
            domesticPackage.getStatus(), domesticPackage.getVersion()
        );
    }

    private User requireActor(String subject) {
        try {
            return userRepository.findById(Long.valueOf(subject))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效"));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效");
        }
    }

    private void requireWriteRole(List<String> roles) {
        if (roles == null || roles.stream().noneMatch(WRITE_ROLES::contains)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RECEIVING_FORBIDDEN", "无权执行收货操作");
        }
    }

    private String packageNo(Instant now) { return "PKG-" + NUMBER_DATE.format(now) + "-" + random(); }
    private String receiptNo(Instant now) { return "GR-" + NUMBER_DATE.format(now) + "-" + random(); }
    private String random() { return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizeForHash(String value) { return value == null ? "" : value.trim(); }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "RECEIPT_INVALID", message); }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private ApiException notReceivable() {
        return conflict("PURCHASE_ORDER_NOT_RECEIVABLE", "采购单当前状态不允许收货");
    }
}
