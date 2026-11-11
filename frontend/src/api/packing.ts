import { requestJson } from '@/api/http'

export type PackingStatus = 'READY' | 'DRAFT' | 'COMPLETED' | 'CANCELED'

export interface PackingTaskListItem {
  taskId: number | null
  orderId: number
  orderNo: string
  storeName: string
  status: PackingStatus
  itemCount: number
  totalQuantity: number
  boxCount: number
  requestedShipAt: string | null
  startedAt: string | null
  completedAt: string | null
}

export interface PackingTaskPage {
  items: PackingTaskListItem[]
  pendingCount: number
  draftCount: number
  completedCount: number
}

export interface PackingAllocation {
  allocationId: number
  allocationNo: string
  lotId: number
  lotNo: string
  orderItemId: number
  productName: string
  variantName: string | null
  quantity: number
  status: 'RESERVED' | 'CONSUMED' | 'RELEASED'
}

export interface PackingBoxItem extends PackingAllocation { id: number }
export interface PackingBox { id: number; boxNo: string; weightGrams: number | null; lengthMm: number | null; widthMm: number | null; heightMm: number | null; note: string | null; version: number; items: PackingBoxItem[] }
export interface PackingEvent { id: number; eventType: string; operatorName: string; remark: string | null; occurredAt: string }
export interface PackingDetail {
  taskId: number | null
  taskNo: string | null
  status: PackingStatus
  version: number
  orderId: number
  orderNo: string
  storeName: string
  requestedShipAt: string | null
  startedAt: string | null
  completedAt: string | null
  allocations: PackingAllocation[]
  boxes: PackingBox[]
  events: PackingEvent[]
  availableActions: string[]
}

export interface BoxItemInput { allocationId: number; quantity: number }
export interface SaveBoxInput { weightGrams: number | null; lengthMm: number | null; widthMm: number | null; heightMm: number | null; note: string; version: number; items: BoxItemInput[] }
export interface PackingCommandResult { taskId: number; taskNo: string; status: PackingStatus; version: number }

export function getPackingTasks(params: { keyword?: string; status?: PackingStatus }) {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.status) query.set('status', params.status)
  return requestJson<PackingTaskPage>(`/api/packing/tasks${query.size ? `?${query}` : ''}`)
}
export function getPackingDetail(orderId: number) { return requestJson<PackingDetail>(`/api/packing/orders/${orderId}`) }
export function startPacking(orderId: number, reason = '') { return requestJson<PackingCommandResult>(`/api/packing/orders/${orderId}/start`, { method: 'POST', body: JSON.stringify({ reason }) }) }
export function addPackingBox(taskId: number, input: SaveBoxInput) { return requestJson<PackingCommandResult>(`/api/packing/tasks/${taskId}/boxes`, { method: 'POST', body: JSON.stringify(input) }) }
export function updatePackingBox(taskId: number, boxId: number, input: SaveBoxInput) { return requestJson<PackingCommandResult>(`/api/packing/tasks/${taskId}/boxes/${boxId}`, { method: 'PUT', body: JSON.stringify(input) }) }
export function deletePackingBox(taskId: number, boxId: number, version: number) { return requestJson<PackingCommandResult>(`/api/packing/tasks/${taskId}/boxes/${boxId}?version=${version}`, { method: 'DELETE' }) }
export function completePacking(taskId: number, version: number) { return requestJson<PackingCommandResult>(`/api/packing/tasks/${taskId}/complete`, { method: 'POST', body: JSON.stringify({ version, idempotencyKey: `packing-${taskId}-${crypto.randomUUID().replace(/-/g, '').slice(0, 20)}` }) }) }
export function cancelPacking(taskId: number, version: number, reason: string) { return requestJson<PackingCommandResult>(`/api/packing/tasks/${taskId}/cancel`, { method: 'POST', body: JSON.stringify({ version, reason }) }) }
