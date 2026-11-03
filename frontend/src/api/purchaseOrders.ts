import { requestJson } from '@/api/http'

export interface PurchaseSupplierSummary { id: number; code: string; name: string }
export interface SupplierDetail extends PurchaseSupplierSummary {
  platform: string; externalSupplierId: string | null; contactName: string | null
  contactPhone: string | null; shopUrl: string | null; note: string | null
  status: string; version: number; createdAt: string; updatedAt: string
}
export interface UserSummary { id: number; username: string; displayName: string }
export interface PurchaseOrderListItem {
  id: number; purchaseOrderNo: string; supplier: PurchaseSupplierSummary
  status: string; currency: string; totalAmountMinor: number
  itemCount: number; purchasedQuantity: number; createdAt: string
}
export interface PurchaseOrderPage {
  items: PurchaseOrderListItem[]; page: number; size: number
  totalElements: number; totalPages: number
}
export interface PurchaseSource {
  allocationId: number; procurementRequestId: number; allocatedQuantity: number
  procurementStatus: string; salesOrderId: number; salesOrderNo: string
  salesOrderItemId: number; productName: string; variantName: string | null
}
export interface PurchaseOrderItem {
  id: number; supplierProductId: string | null; supplierSku: string | null
  productName: string; variantName: string | null; productUrl: string | null
  imageUrl: string | null; purchasedQuantity: number; receivedQuantity: number
  unitPriceMinor: number | null; currency: string; note: string | null
  version: number; sources: PurchaseSource[]
}
export interface PurchaseOrderDetail {
  id: number; purchaseOrderNo: string; supplier: SupplierDetail; status: string
  externalOrderNo: string | null; currency: string; totalAmountMinor: number
  orderedAt: string | null; expectedArrivalAt: string | null
  createdBy: UserSummary; note: string | null; version: number
  createdAt: string; updatedAt: string; items: PurchaseOrderItem[]
}
export interface PurchaseOrderEvent {
  id: number; eventType: string; fromValue: string | null; toValue: string | null
  operator: UserSummary | null; remark: string | null; occurredAt: string
}
export interface PurchaseOrderEventPage {
  items: PurchaseOrderEvent[]; page: number; size: number
  totalElements: number; totalPages: number
}
export interface PurchaseOrderQuery {
  page: number; size: number; keyword?: string; status?: string; sort?: string
}

export function getPurchaseOrders(query: PurchaseOrderQuery) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.status) params.set('status', query.status)
  if (query.sort) params.set('sort', query.sort)
  return requestJson<PurchaseOrderPage>(`/api/purchase-orders?${params.toString()}`)
}

export function getPurchaseOrderDetail(orderId: number) {
  return requestJson<PurchaseOrderDetail>(`/api/purchase-orders/${orderId}`)
}

export function getPurchaseOrderEvents(orderId: number, page = 1, size = 10) {
  return requestJson<PurchaseOrderEventPage>(`/api/purchase-orders/${orderId}/events?page=${page}&size=${size}`)
}
