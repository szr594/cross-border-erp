package com.szr.crosserp.inventory.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.model.*;
import com.szr.crosserp.inventory.repository.*;
import com.szr.crosserp.user.model.User;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InventoryPackingService {
    private final InventoryAllocationRepository allocations;
    private final InventoryLotRepository lots;
    private final InventoryMovementRepository movements;
    public InventoryPackingService(InventoryAllocationRepository allocations,InventoryLotRepository lots,InventoryMovementRepository movements){this.allocations=allocations;this.lots=lots;this.movements=movements;}
    public void consume(List<Long> allocationIds,User actor,String completionKey,Instant now){
        List<Long> sortedAllocationIds=allocationIds.stream().distinct().sorted().toList();
        List<Long> lotIds=sortedAllocationIds.stream()
            .map(id->allocations.findById(id).orElseThrow(()->conflict("PACKING_ALLOCATION_NOT_FOUND","库存分配不存在")))
            .map(a->a.getInventoryLot().getId()).distinct().sorted().toList();
        Map<Long,InventoryLot> lockedLots=new HashMap<>();
        for(Long id:lotIds) lockedLots.put(id,lots.findByIdForUpdate(id).orElseThrow(()->conflict("PACKING_LOT_NOT_FOUND","库存批次不存在")));
        List<InventoryAllocation> locked=sortedAllocationIds.stream()
            .map(id->allocations.findByIdForUpdate(id).orElseThrow(()->conflict("PACKING_ALLOCATION_NOT_FOUND","库存分配不存在"))).toList();
        for(InventoryAllocation allocation:locked){
            if(allocation.getStatus()!=InventoryAllocationStatus.RESERVED) throw conflict("PACKING_ALLOCATION_NOT_RESERVED","仅预留库存可以用于打包");
            InventoryLot lot=lockedLots.get(allocation.getInventoryLot().getId());
            lot.consumeReservation(allocation.getQuantity()); allocation.consume();
            movements.save(new InventoryMovement(number("IMV"),lot,allocation,InventoryMovementType.PACKING_CONSUMED,allocation.getQuantity(),lot.getAvailableQuantity(),lot.getReservedQuantity(),lot.getQuarantinedQuantity(),actor,"打包完成",completionKey+"-"+allocation.getId(),now));
        }
        allocations.flush(); lots.flush(); movements.flush();
    }
    private String number(String prefix){return prefix+"-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase();}
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
