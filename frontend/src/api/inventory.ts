import { requestJson } from '@/api/http'

export type InventoryLotStatus = 'AVAILABLE' | 'ALLOCATED' | 'QUARANTINED' | 'DEPLETED' | 'CLOSED'
export type InventoryQualityStatus = 'ACCEPTED' | 'DAMAGED'
export type InventoryAllocationStatus = 'RESERVED' | 'CONSUMED' | 'RELEASED'
export type InventoryMovementType = 'RECEIPT_ACCEPTED' | 'RECEIPT_DAMAGED' | 'ORDER_ALLOCATED'
  | 'ALLOCATION_RELEASED' | 'PACKING_CONSUMED' | 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT'
  | 'QUARANTINE_RELEASED'

export interface InventoryProduct {
  purchaseOrderItemId: number
  supplierSku: string | null
  productName: string
  variantName: string | null
  imageUrl: string | null
}

export interface InventoryPurchaseSource {
  purchaseOrderId: number
  purchaseOrderNo: string
  status: string
  supplierId: number
  supplierCode: string
  supplierName: string
}

export interface InventoryReceiptSource {
  receiptId: number
  receiptNo: string
  packageId: number
  packageNo: string
  carrier: string
  trackingNumber: string
  confirmedAt: string
}

export interface InventoryAllocation {
  id: number
  allocationNo: string
  lotId: number
  lotNo: string
  salesOrderId: number
  orderNo: string
  salesOrderItemId: number
  productName: string
  variantName: string | null
  quantity: number
  status: InventoryAllocationStatus
  version: number
  allocatedAt: string
}

export interface InventoryLotListItem {
  id: number
  lotNo: string
  product: InventoryProduct
  purchaseSource: InventoryPurchaseSource
  qualityStatus: InventoryQualityStatus
  status: InventoryLotStatus
  initialQuantity: number
  availableQuantity: number
  reservedQuantity: number
  quarantinedQuantity: number
  consumedQuantity: number
  adjustedOutQuantity: number
  receivedAt: string
}

export interface InventoryLotDetail extends InventoryLotListItem {
  receiptSource: InventoryReceiptSource
  version: number
  createdAt: string
  updatedAt: string
  allocations: InventoryAllocation[]
}

export interface InventoryLotPage {
  items: InventoryLotListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface InventoryMovement {
  id: number
  movementNo: string
  movementType: InventoryMovementType
  quantity: number
  availableAfter: number
  reservedAfter: number
  quarantinedAfter: number
  allocationId: number | null
  allocationNo: string | null
  operator: { id: number; loginName: string; displayName: string } | null
  reason: string | null
  idempotencyKey: string
  occurredAt: string
}

export interface InventoryMovementPage {
  items: InventoryMovement[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface InventoryLotQuery {
  page: number
  size: number
  keyword?: string
  purchaseOrderId?: number
  status?: InventoryLotStatus
  qualityStatus?: InventoryQualityStatus
}

export interface InventoryCommandResponse {
  lotId: number
  lotNo: string
  allocationId: number | null
  allocationNo: string | null
  allocationStatus: string | null
  availableQuantity: number
  reservedQuantity: number
  quarantinedQuantity: number
  lotVersion: number
  allocationVersion: number | null
  movementId: number
  movementType: string
}

export function getInventoryLots(query: InventoryLotQuery) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.purchaseOrderId) params.set('purchaseOrderId', String(query.purchaseOrderId))
  if (query.status) params.set('status', query.status)
  if (query.qualityStatus) params.set('qualityStatus', query.qualityStatus)
  return requestJson<InventoryLotPage>(`/api/inventory/lots?${params.toString()}`)
}

export function getInventoryLot(lotId: number) {
  return requestJson<InventoryLotDetail>(`/api/inventory/lots/${lotId}`)
}

export function getInventoryMovements(lotId: number, page: number, size: number) {
  return requestJson<InventoryMovementPage>(`/api/inventory/lots/${lotId}/movements?page=${page}&size=${size}`)
}

export function allocateInventory(lotId: number, payload: {
  orderItemId: number; quantity: number; reason: string; idempotencyKey: string; version: number
}) {
  return requestJson<InventoryCommandResponse>(`/api/inventory/lots/${lotId}/allocate`, {
    method: 'POST', body: JSON.stringify(payload),
  })
}

export function releaseInventoryAllocation(allocationId: number, payload: {
  reason: string; idempotencyKey: string; version: number
}) {
  return requestJson<InventoryCommandResponse>(`/api/inventory/allocations/${allocationId}/release`, {
    method: 'POST', body: JSON.stringify(payload),
  })
}

export function adjustInventory(lotId: number, payload: {
  type: 'IN' | 'OUT'; quantity: number; reason: string; idempotencyKey: string; version: number
}) {
  return requestJson<InventoryCommandResponse>(`/api/inventory/lots/${lotId}/adjustments`, {
    method: 'POST', body: JSON.stringify(payload),
  })
}

export function releaseQuarantine(lotId: number, payload: {
  quantity: number; reason: string; idempotencyKey: string; version: number
}) {
  return requestJson<InventoryCommandResponse>(`/api/inventory/lots/${lotId}/release-quarantine`, {
    method: 'POST', body: JSON.stringify(payload),
  })
}
