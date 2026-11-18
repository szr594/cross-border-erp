import { requestJson } from './http'
export interface SupplierOption { id: number; code: string; name: string }
export function searchActiveSuppliers(keyword: string) {
  return requestJson<{ items: SupplierOption[] }>('/api/suppliers?page=1&size=100&status=ACTIVE&keyword=' + encodeURIComponent(keyword))
}
