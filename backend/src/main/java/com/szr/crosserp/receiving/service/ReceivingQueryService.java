package com.szr.crosserp.receiving.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.procurement.model.PurchaseOrderItem;
import com.szr.crosserp.procurement.repository.PurchaseOrderItemRepository;
import com.szr.crosserp.receiving.dto.DomesticPackageDetailResponse;
import com.szr.crosserp.receiving.dto.DomesticPackageListItemResponse;
import com.szr.crosserp.receiving.dto.DomesticPackagePageResponse;
import com.szr.crosserp.receiving.dto.DomesticPackageQuery;
import com.szr.crosserp.receiving.dto.GoodsReceiptDetailResponse;
import com.szr.crosserp.receiving.dto.GoodsReceiptItemResponse;
import com.szr.crosserp.receiving.dto.GoodsReceiptSummaryResponse;
import com.szr.crosserp.receiving.dto.ReceivingEventPageResponse;
import com.szr.crosserp.receiving.dto.ReceivingEventResponse;
import com.szr.crosserp.receiving.dto.ReceivingPurchaseItemResponse;
import com.szr.crosserp.receiving.dto.ReceivingSupplierResponse;
import com.szr.crosserp.receiving.dto.UnexpectedReceiptItemResponse;
import com.szr.crosserp.receiving.model.DomesticPackage;
import com.szr.crosserp.receiving.model.GoodsReceipt;
import com.szr.crosserp.receiving.model.GoodsReceiptItem;
import com.szr.crosserp.receiving.repository.DomesticPackageRepository;
import com.szr.crosserp.receiving.repository.DomesticPackageSpecifications;
import com.szr.crosserp.receiving.repository.GoodsReceiptItemRepository;
import com.szr.crosserp.receiving.repository.GoodsReceiptRepository;
import com.szr.crosserp.receiving.repository.ReceivingEventRepository;
import com.szr.crosserp.receiving.repository.UnexpectedReceiptItemRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReceivingQueryService {
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "trackingNumber", "status", "firstReceivedAt");
    private static final Set<String> WRITE_ROLES = Set.of("OWNER", "WAREHOUSE");

    private final DomesticPackageRepository packageRepository;
    private final PurchaseOrderItemRepository purchaseItemRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptItemRepository receiptItemRepository;
    private final UnexpectedReceiptItemRepository unexpectedItemRepository;
    private final ReceivingEventRepository eventRepository;

    public ReceivingQueryService(
        DomesticPackageRepository packageRepository,
        PurchaseOrderItemRepository purchaseItemRepository,
        GoodsReceiptRepository receiptRepository,
        GoodsReceiptItemRepository receiptItemRepository,
        UnexpectedReceiptItemRepository unexpectedItemRepository,
        ReceivingEventRepository eventRepository
    ) {
        this.packageRepository = packageRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.unexpectedItemRepository = unexpectedItemRepository;
        this.eventRepository = eventRepository;
    }

    public DomesticPackagePageResponse findPackages(DomesticPackageQuery query) {
        validate(query);
        Page<DomesticPackage> result = packageRepository.findAll(
            DomesticPackageSpecifications.from(query),
            PageRequest.of(query.page() - 1, query.size(), parseSort(query.sort()))
        );
        return new DomesticPackagePageResponse(
            result.getContent().stream().map(this::listItem).toList(),
            query.page(), query.size(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public DomesticPackageDetailResponse findByTrackingNumber(String trackingNumber, List<String> roles) {
        if (trackingNumber == null || trackingNumber.isBlank() || trackingNumber.trim().length() > 128) {
            throw invalid("trackingNumber 无效");
        }
        List<DomesticPackage> matches = packageRepository.findByTrackingNumberIgnoreCase(trackingNumber.trim());
        if (matches.isEmpty()) {
            throw notFound("物流单号未登记");
        }
        if (matches.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "TRACKING_NUMBER_AMBIGUOUS", "多个承运商使用该物流单号，请从列表中选择");
        }
        DomesticPackage domesticPackage = matches.get(0);
        return detail(domesticPackage, roles);
    }

    public DomesticPackageDetailResponse findDetail(Long packageId, List<String> roles) {
        return detail(requirePackage(packageId), roles);
    }

    public GoodsReceiptDetailResponse findReceipt(Long receiptId) {
        GoodsReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOODS_RECEIPT_NOT_FOUND", "收货批次不存在"));
        List<GoodsReceiptItemResponse> items = receiptItemRepository.findByGoodsReceiptIdOrderByIdAsc(receiptId).stream()
            .map(item -> new GoodsReceiptItemResponse(
                item.getId(), item.getPurchaseOrderItem().getId(), item.getPurchaseOrderItem().getProductName(),
                item.getPurchaseOrderItem().getVariantName(), item.getAcceptedQuantity(),
                item.getDamagedQuantity(), item.getNote()
            )).toList();
        List<UnexpectedReceiptItemResponse> unexpected = unexpectedItemRepository
            .findByGoodsReceiptIdOrderByIdAsc(receiptId).stream()
            .map(item -> new UnexpectedReceiptItemResponse(
                item.getId(), item.getDescription(), item.getSupplierSku(), item.getQuantity(), item.getNote()
            )).toList();
        return new GoodsReceiptDetailResponse(
            receipt.getId(), receipt.getReceiptNo(), receipt.getDomesticPackage().getId(),
            receipt.getDomesticPackage().getPackageNo(), receipt.getOperator().getDisplayName(),
            receipt.getConfirmedAt(), receipt.getNote(), false, items, unexpected
        );
    }

    public ReceivingEventPageResponse findEvents(Long packageId, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw invalid("事件分页参数无效");
        }
        requirePackage(packageId);
        var result = eventRepository.findByDomesticPackageId(
            packageId,
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"))
        );
        return new ReceivingEventPageResponse(
            result.getContent().stream().map(event -> new ReceivingEventResponse(
                event.getId(), event.getEventType(),
                event.getGoodsReceipt() == null ? null : event.getGoodsReceipt().getId(),
                event.getOperator().getDisplayName(), event.getRemark(), event.getOccurredAt()
            )).toList(), page, size, result.getTotalElements(), result.getTotalPages()
        );
    }

    private DomesticPackageListItemResponse listItem(DomesticPackage domesticPackage) {
        List<PurchaseOrderItem> purchaseItems = purchaseItemRepository.findByPurchaseOrderIdOrderByIdAsc(
            domesticPackage.getPurchaseOrder().getId()
        );
        List<GoodsReceiptItem> receiptItems = receiptItemRepository.findByPurchaseOrderItemIdInOrderByIdAsc(
            purchaseItems.stream().map(PurchaseOrderItem::getId).toList()
        );
        return new DomesticPackageListItemResponse(
            domesticPackage.getId(), domesticPackage.getPackageNo(), domesticPackage.getTrackingNumber(),
            domesticPackage.getCarrier(), domesticPackage.getStatus(), domesticPackage.getPurchaseOrder().getId(),
            domesticPackage.getPurchaseOrder().getPurchaseOrderNo(), supplier(domesticPackage),
            purchaseItems.stream().mapToInt(PurchaseOrderItem::getPurchasedQuantity).sum(),
            purchaseItems.stream().mapToInt(PurchaseOrderItem::getReceivedQuantity).sum(),
            receiptItems.stream().mapToInt(GoodsReceiptItem::getDamagedQuantity).sum(),
            domesticPackage.getFirstReceivedAt(), domesticPackage.getCompletedAt(), domesticPackage.getCreatedAt()
        );
    }

    private DomesticPackageDetailResponse detail(DomesticPackage domesticPackage, List<String> roles) {
        Long orderId = domesticPackage.getPurchaseOrder().getId();
        List<PurchaseOrderItem> purchaseItems = purchaseItemRepository.findByPurchaseOrderIdOrderByIdAsc(orderId);
        List<GoodsReceiptItem> receiptItems = receiptItemRepository.findByPurchaseOrderItemIdInOrderByIdAsc(
            purchaseItems.stream().map(PurchaseOrderItem::getId).toList()
        );
        Map<Long, Integer> damagedByItem = new HashMap<>();
        receiptItems.forEach(item -> damagedByItem.merge(
            item.getPurchaseOrderItem().getId(), item.getDamagedQuantity(), Integer::sum
        ));
        List<GoodsReceipt> receipts = receiptRepository.findByDomesticPackageIdOrderByConfirmedAtDescIdDesc(
            domesticPackage.getId()
        );
        List<Long> receiptIds = receipts.stream().map(GoodsReceipt::getId).toList();
        Map<Long, int[]> totalsByReceipt = receiptTotals(receiptIds);
        return new DomesticPackageDetailResponse(
            domesticPackage.getId(), domesticPackage.getPackageNo(), domesticPackage.getTrackingNumber(),
            domesticPackage.getCarrier(), domesticPackage.getStatus(), orderId,
            domesticPackage.getPurchaseOrder().getPurchaseOrderNo(), domesticPackage.getPurchaseOrder().getStatus(),
            supplier(domesticPackage), domesticPackage.getExpectedAt(), domesticPackage.getFirstReceivedAt(),
            domesticPackage.getCompletedAt(), domesticPackage.getCreatedBy().getDisplayName(), domesticPackage.getNote(),
            domesticPackage.getVersion(), domesticPackage.getCreatedAt(), domesticPackage.getUpdatedAt(),
            purchaseItems.stream().map(item -> {
                int damaged = damagedByItem.getOrDefault(item.getId(), 0);
                return new ReceivingPurchaseItemResponse(
                    item.getId(), item.getSupplierSku(), item.getProductName(), item.getVariantName(),
                    item.getPurchasedQuantity(), item.getReceivedQuantity(), damaged,
                    Math.max(0, item.getPurchasedQuantity() - item.getReceivedQuantity() - damaged), item.getVersion()
                );
            }).toList(),
            receipts.stream().map(receipt -> {
                int[] totals = totalsByReceipt.getOrDefault(receipt.getId(), new int[3]);
                return new GoodsReceiptSummaryResponse(
                    receipt.getId(), receipt.getReceiptNo(), receipt.getOperator().getDisplayName(),
                    receipt.getConfirmedAt(), totals[0], totals[1], totals[2]
                );
            }).toList(), availableActions(domesticPackage, roles)
        );
    }

    private Map<Long, int[]> receiptTotals(List<Long> receiptIds) {
        Map<Long, int[]> totals = new HashMap<>();
        if (receiptIds.isEmpty()) {
            return totals;
        }
        receiptItemRepository.findByGoodsReceiptIdInOrderByIdAsc(receiptIds).forEach(item -> {
            int[] value = totals.computeIfAbsent(item.getGoodsReceipt().getId(), ignored -> new int[3]);
            value[0] += item.getAcceptedQuantity();
            value[1] += item.getDamagedQuantity();
        });
        unexpectedItemRepository.findByGoodsReceiptIdInOrderByIdAsc(receiptIds).forEach(item ->
            totals.computeIfAbsent(item.getGoodsReceipt().getId(), ignored -> new int[3])[2] += item.getQuantity()
        );
        return totals;
    }

    private List<String> availableActions(DomesticPackage domesticPackage, List<String> roles) {
        boolean canWrite = roles != null && roles.stream().anyMatch(WRITE_ROLES::contains);
        boolean receivable = Set.of("ORDERED", "PARTIALLY_RECEIVED")
            .contains(domesticPackage.getPurchaseOrder().getStatus().name());
        if (!canWrite || !receivable || domesticPackage.getStatus().name().equals("CANCELED")) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        actions.add("CONFIRM_RECEIPT");
        if (!receiptRepository.existsByDomesticPackageId(domesticPackage.getId())) {
            actions.add("CANCEL_PACKAGE");
        }
        return actions;
    }

    private void validate(DomesticPackageQuery query) {
        if (query.page() < 1 || query.size() < 1 || query.size() > 100) {
            throw invalid("分页参数无效");
        }
        if (query.keyword() != null && query.keyword().trim().length() > 200) {
            throw invalid("keyword 长度不能超过200个字符");
        }
    }

    private Sort parseSort(String value) {
        String normalized = value == null || value.isBlank() ? "createdAt,desc" : value.trim();
        String[] parts = normalized.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])) {
            throw invalid("sort 字段无效");
        }
        try {
            return Sort.by(Sort.Direction.fromString(parts[1].toUpperCase(Locale.ROOT)), parts[0])
                .and(Sort.by(Sort.Direction.DESC, "id"));
        } catch (IllegalArgumentException exception) {
            throw invalid("sort 方向必须是 asc 或 desc");
        }
    }

    private DomesticPackage requirePackage(Long packageId) {
        return packageRepository.findById(packageId).orElseThrow(() -> notFound("国内包裹不存在"));
    }

    private ReceivingSupplierResponse supplier(DomesticPackage domesticPackage) {
        var supplier = domesticPackage.getPurchaseOrder().getSupplier();
        return new ReceivingSupplierResponse(supplier.getId(), supplier.getCode(), supplier.getName());
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "RECEIVING_QUERY_INVALID", message);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "DOMESTIC_PACKAGE_NOT_FOUND", message);
    }
}
