package com.szr.crosserp.procurement.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.procurement.dto.CreateSupplierRequest;
import com.szr.crosserp.procurement.dto.SupplierPageResponse;
import com.szr.crosserp.procurement.dto.SupplierResponse;
import com.szr.crosserp.procurement.dto.UpdateSupplierRequest;
import com.szr.crosserp.procurement.model.Supplier;
import com.szr.crosserp.procurement.model.SupplierStatus;
import com.szr.crosserp.procurement.repository.SupplierRepository;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {
    private static final Set<String> WRITE_ROLES = Set.of("OWNER", "PROCUREMENT");

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public SupplierPageResponse findSuppliers(int page, int size, String keyword, SupplierStatus status) {
        if (page < 1 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_QUERY_INVALID", "分页参数无效");
        }
        String normalizedKeyword = normalizeNullable(keyword);
        Page<Supplier> result = supplierRepository.search(
            normalizedKeyword,
            status,
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "code"))
        );
        return new SupplierPageResponse(
            result.getContent().stream().map(this::response).toList(),
            page,
            size,
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public SupplierResponse findSupplier(Long supplierId) {
        return response(requireSupplier(supplierId));
    }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest command, List<String> roles) {
        requireWriteRole(roles);
        String code = command.code().trim().toUpperCase();
        String externalId = normalizeNullable(command.externalSupplierId());
        assertUnique(code, command.platform(), externalId, null);
        Supplier supplier = new Supplier(
            code,
            command.name().trim(),
            command.platform(),
            externalId,
            normalizeNullable(command.contactName()),
            normalizeNullable(command.contactPhone()),
            normalizeNullable(command.shopUrl()),
            normalizeNullable(command.note())
        );
        return response(save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long supplierId, UpdateSupplierRequest command, List<String> roles) {
        requireWriteRole(roles);
        Supplier supplier = requireSupplier(supplierId);
        assertVersion(supplier, command.version());
        String externalId = normalizeNullable(command.externalSupplierId());
        assertUnique(supplier.getCode(), command.platform(), externalId, supplierId);
        supplier.update(
            command.name().trim(),
            command.platform(),
            externalId,
            normalizeNullable(command.contactName()),
            normalizeNullable(command.contactPhone()),
            normalizeNullable(command.shopUrl()),
            normalizeNullable(command.note())
        );
        return response(save(supplier));
    }

    @Transactional
    public SupplierResponse changeStatus(
        Long supplierId, long version, SupplierStatus status, List<String> roles
    ) {
        requireWriteRole(roles);
        Supplier supplier = requireSupplier(supplierId);
        assertVersion(supplier, version);
        if (status == SupplierStatus.ACTIVE) {
            supplier.enable();
        } else {
            supplier.disable();
        }
        return response(save(supplier));
    }

    private Supplier save(Supplier supplier) {
        try {
            return supplierRepository.saveAndFlush(supplier);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_DUPLICATE", "供应商编号或外部供应商编号已存在");
        }
    }

    private void assertUnique(
        String code,
        com.szr.crosserp.procurement.model.SupplierPlatform platform,
        String externalId,
        Long currentId
    ) {
        supplierRepository.findByCode(code)
            .filter(existing -> !existing.getId().equals(currentId))
            .ifPresent(existing -> duplicate());
        if (externalId != null) {
            supplierRepository.findByPlatformAndExternalSupplierId(platform, externalId)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> duplicate());
        }
    }

    private void duplicate() {
        throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_DUPLICATE", "供应商编号或外部供应商编号已存在");
    }

    private Supplier requireSupplier(Long supplierId) {
        return supplierRepository.findById(supplierId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "供应商不存在"));
    }

    private void assertVersion(Supplier supplier, long version) {
        if (supplier.getVersion() != version) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_VERSION_CONFLICT", "供应商已被修改，请刷新后重试");
        }
    }

    private void requireWriteRole(List<String> roles) {
        if (roles == null || roles.stream().noneMatch(WRITE_ROLES::contains)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SUPPLIER_COMMAND_FORBIDDEN", "无权修改供应商档案");
        }
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private SupplierResponse response(Supplier supplier) {
        return new SupplierResponse(
            supplier.getId(), supplier.getCode(), supplier.getName(), supplier.getPlatform(),
            supplier.getExternalSupplierId(), supplier.getContactName(), supplier.getContactPhone(),
            supplier.getShopUrl(), supplier.getNote(), supplier.getStatus(), supplier.getVersion(),
            supplier.getCreatedAt(), supplier.getUpdatedAt()
        );
    }
}
