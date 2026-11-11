import { requestJson } from './http'

export interface OrderImportResult {
  importId: string
  status: 'VALIDATING' | 'VALIDATED' | 'INVALID' | 'IMPORTING' | 'COMPLETED' | 'FAILED' | 'EXPIRED'
  fileName: string
  totalRows: number
  orderCount: number
  itemCount: number
  creatableOrderCount: number
  skippedOrderCount: number
  errorCount: number
  createdAt: string
  expiresAt: string
  confirmedAt: string | null
  completedAt: string | null
}

export interface OrderImportErrorItem {
  rowNumber: number
  field: string | null
  code: string
  message: string
}

export interface OrderImportErrorPage {
  items: OrderImportErrorItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function previewOrderImport(file: File) {
  const body = new FormData()
  body.append('file', file)
  return requestJson<OrderImportResult>('/api/order-imports/preview', { method: 'POST', body })
}

export function getOrderImport(importId: string) {
  return requestJson<OrderImportResult>(`/api/order-imports/${importId}`)
}

export function getOrderImportErrors(importId: string, page = 1, size = 20) {
  return requestJson<OrderImportErrorPage>(`/api/order-imports/${importId}/errors?page=${page}&size=${size}`)
}

export function confirmOrderImport(importId: string) {
  return requestJson<OrderImportResult>(`/api/order-imports/${importId}/confirm`, { method: 'POST' })
}
