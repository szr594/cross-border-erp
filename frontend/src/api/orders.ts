import { requestJson } from './http'

export interface NamedOption { value: string; label: string }
export interface StoreSummary { id: number; code: string; name: string }
export interface UserSummary { id: number; loginName: string; displayName: string }
export interface OrderFilterOptions {
  stores: StoreSummary[]
  responsibleUsers: UserSummary[]
  externalStatuses: NamedOption[]
  fulfillmentStages: NamedOption[]
}
export interface OrderSummary { pending: number; waitingProcurement: number; readyForPacking: number; exception: number }
export interface OrderListItem {
  id: number; orderNo: string; displayOrderNo: string | null; store: StoreSummary
  responsibleUser: UserSummary | null; externalStatus: string; stage: string; stageName: string
  exception: boolean; orderedAt: string | null; requestedShipAt: string | null
  itemCount: number; totalQuantity: number; productNames: string[]
}
export interface OrderPage {
  items: OrderListItem[]; page: number; size: number; totalElements: number; totalPages: number; summary: OrderSummary
}
export interface OrderItem {
  id: number; externalItemId: string; externalSku: string | null; erpSkuId: number | null
  productName: string; variantName: string | null; imageUrl: string | null; quantity: number
  unitPriceMinor: number | null; currency: string | null; fulfillmentStatus: string
  inventoryAllocatedQuantity: number; procurementRequestedQuantity: number
  procurementInProgressQuantity: number; receivedQuantity: number; missingQuantity: number; ready: boolean
}
export interface CustomerDetail {
  customerId: string | null; customerName: string | null; recipientName: string | null
  recipientPhone?: string | null; countryCode: string | null; province?: string | null
  city?: string | null; district?: string | null; addressLine1?: string | null
  addressLine2?: string | null; postalCode?: string | null
}
export interface OrderDetail {
  id: number; orderNo: string; externalOrderId: string; displayOrderNo: string | null
  store: StoreSummary; responsibleUser: UserSummary | null; externalStatus: string; externalStatusRaw: string | null
  stage: string; stageName: string; exception: boolean; exceptionReason: string | null; currency: string | null
  orderedAt: string | null; shipAfter: string | null; requestedShipAt: string | null
  customer: CustomerDetail; note: string | null; version: number; items: OrderItem[]; availableActions: string[]
}
export interface OrderFulfillment { orderId: number; stage: string; stageName: string; items: OrderItem[] }
export interface OrderEvent {
  id: number; eventType: string; fromValue: string | null; toValue: string | null
  operator: UserSummary | null; source: string; remark: string | null; occurredAt: string
}
export interface OrderEventPage { items: OrderEvent[]; page: number; size: number; totalElements: number; totalPages: number }
export interface OrderListQuery {
  page: number; size: number; keyword?: string; storeId?: number; responsibleUserId?: number
  externalStatus?: string; stage?: string; orderedFrom?: string; orderedTo?: string
  exceptionOnly?: boolean; sort?: string
}
export interface VersionedReasonCommand { reason: string; version: number }
export interface ResponsibleUserCommand extends VersionedReasonCommand { userId: number }
export interface ProcurementItemCommand { orderItemId: number; quantity: number }
export interface ProcurementCommand { items: ProcurementItemCommand[]; note?: string; version: number }
export interface ResolveExceptionCommand { resolution: string; version: number }
export interface ResponsibleUserChangeResponse { orderId: number; responsibleUser: UserSummary; version: number }
export interface ProcurementRequestResponse {
  id: number; orderItemId: number; quantity: number; status: string; version: number
}
export interface ProcurementRequestBatchResponse { requests: ProcurementRequestResponse[]; version: number }
export interface OrderCommandResponse { orderId: number; externalStatus: string; exception: boolean; version: number }

export function getOrderFilterOptions() { return requestJson<OrderFilterOptions>('/api/orders/filter-options') }
export function getOrders(query: OrderListQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '' && value !== false) params.set(key, String(value))
  })
  return requestJson<OrderPage>(`/api/orders?${params.toString()}`)
}
export function getOrderDetail(orderId: number) { return requestJson<OrderDetail>(`/api/orders/${orderId}`) }
export function getOrderFulfillment(orderId: number) { return requestJson<OrderFulfillment>(`/api/orders/${orderId}/fulfillment`) }
export function getOrderEvents(orderId: number, page = 1, size = 20) {
  return requestJson<OrderEventPage>(`/api/orders/${orderId}/events?page=${page}&size=${size}`)
}
function command<T>(path: string, method: 'POST' | 'PUT', body: object) {
  return requestJson<T>(path, { method, body: JSON.stringify(body) })
}
export function changeResponsibleUser(orderId: number, body: ResponsibleUserCommand) {
  return command<ResponsibleUserChangeResponse>(`/api/orders/${orderId}/responsible-user`, 'PUT', body)
}
export function createProcurementRequests(orderId: number, body: ProcurementCommand) {
  return command<ProcurementRequestBatchResponse>(`/api/orders/${orderId}/procurement-requests`, 'POST', body)
}
export function withdrawProcurementRequest(orderId: number, requestId: number, body: VersionedReasonCommand) {
  return command<ProcurementRequestResponse>(`/api/orders/${orderId}/procurement-requests/${requestId}/withdraw`, 'POST', body)
}
export function openOrderException(orderId: number, body: VersionedReasonCommand) {
  return command<OrderCommandResponse>(`/api/orders/${orderId}/exceptions`, 'POST', body)
}
export function resolveOrderException(orderId: number, body: ResolveExceptionCommand) {
  return command<OrderCommandResponse>(`/api/orders/${orderId}/exceptions/resolve`, 'POST', body)
}
export function cancelOrder(orderId: number, body: VersionedReasonCommand) {
  return command<OrderCommandResponse>(`/api/orders/${orderId}/cancel`, 'POST', body)
}
