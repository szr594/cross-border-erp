import { requestJson } from '@/api/http'

export interface ReceivingSupplier { id: number; code: string; name: string }
export interface DomesticPackageListItem {
  id: number; packageNo: string; trackingNumber: string; carrier: string; status: string
  purchaseOrderId: number; purchaseOrderNo: string; supplier: ReceivingSupplier
  purchasedQuantity: number; acceptedQuantity: number; damagedQuantity: number
  firstReceivedAt: string | null; completedAt: string | null; createdAt: string
}
export interface DomesticPackagePage {
  items: DomesticPackageListItem[]; page: number; size: number
  totalElements: number; totalPages: number
}
export interface ReceivingPurchaseItem {
  id: number; supplierSku: string | null; productName: string; variantName: string | null
  purchasedQuantity: number; acceptedQuantity: number; damagedQuantity: number
  remainingQuantity: number; version: number
}
export interface GoodsReceiptSummary {
  id: number; receiptNo: string; operatorName: string; confirmedAt: string
  acceptedQuantity: number; damagedQuantity: number; unexpectedQuantity: number
}
export interface DomesticPackageDetail extends DomesticPackageListItem {
  purchaseOrderStatus: string; expectedAt: string | null; createdByName: string
  note: string | null; version: number; updatedAt: string
  items: ReceivingPurchaseItem[]; receipts: GoodsReceiptSummary[]; availableActions: string[]
}
export interface GoodsReceiptItem {
  id: number; purchaseOrderItemId: number; productName: string; variantName: string | null
  acceptedQuantity: number; damagedQuantity: number; note: string | null
}
export interface UnexpectedReceiptItem {
  id: number; description: string; supplierSku: string | null; quantity: number; note: string | null
}
export interface GoodsReceiptDetail {
  id: number; receiptNo: string; packageId: number; packageNo: string; operatorName: string
  confirmedAt: string; note: string | null; inventoryPosted: boolean
  items: GoodsReceiptItem[]; unexpectedItems: UnexpectedReceiptItem[]
}
export interface DomesticPackageQuery {
  page: number; size: number; keyword?: string; status?: string; sort?: string
}
export interface CreateDomesticPackagePayload {
  purchaseOrderId: number; trackingNumber: string; carrier: string; expectedAt?: string; note?: string
}
export interface ConfirmReceiptItemPayload {
  purchaseOrderItemId: number; acceptedQuantity: number; damagedQuantity: number; note?: string
}
export interface UnexpectedReceiptItemPayload {
  description: string; supplierSku?: string; quantity: number; note?: string
}
export interface ConfirmGoodsReceiptPayload {
  idempotencyKey: string; packageVersion: number
  items: ConfirmReceiptItemPayload[]; unexpectedItems: UnexpectedReceiptItemPayload[]; note?: string
}

export function getDomesticPackages(query: DomesticPackageQuery) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.status) params.set('status', query.status)
  if (query.sort) params.set('sort', query.sort)
  return requestJson<DomesticPackagePage>(`/api/domestic-packages?${params.toString()}`)
}

export function lookupDomesticPackage(trackingNumber: string) {
  return requestJson<DomesticPackageDetail>(`/api/domestic-packages/lookup?trackingNumber=${encodeURIComponent(trackingNumber)}`)
}

export function getDomesticPackage(packageId: number) {
  return requestJson<DomesticPackageDetail>(`/api/domestic-packages/${packageId}`)
}

export function createDomesticPackage(payload: CreateDomesticPackagePayload) {
  return requestJson<{ id: number }>('/api/domestic-packages', {
    method: 'POST', body: JSON.stringify(payload),
  })
}

export function confirmGoodsReceipt(packageId: number, payload: ConfirmGoodsReceiptPayload) {
  return requestJson<{ receiptId: number; packageStatus: string; purchaseOrderStatus: string }>(
    `/api/domestic-packages/${packageId}/receipts`,
    { method: 'POST', body: JSON.stringify(payload) },
  )
}

export function getGoodsReceipt(receiptId: number) {
  return requestJson<GoodsReceiptDetail>(`/api/goods-receipts/${receiptId}`)
}
