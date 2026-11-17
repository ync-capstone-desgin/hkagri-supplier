import { api } from './client'
import type { PageResponse } from './types'

/** 변경 이력을 남기는 대상 종류 */
export type ChangeTarget = 'SUPPLIER' | 'CONTRACT' | 'CERTIFICATION'

/** 바뀐 항목 하나: 항목 이름, 이전 값, 바뀐 값 (없으면 null) */
export interface FieldChange {
  label: string
  before: string | null
  after: string | null
}

/** 변경 이력 한 줄 */
export interface ChangeItem {
  id: number
  targetType: ChangeTarget
  targetLabel: string
  targetId: number
  supplierId: number
  supplierName: string
  targetName: string
  action: 'CREATE' | 'UPDATE' | 'RENEW' | 'STATUS' | 'DELETE'
  actionLabel: string
  changes: FieldChange[]
  actor: string
  actorName: string
  createdAt: string
}

/** 한 업체와 그 업체의 계약·인증 변경 이력을 최신순으로 조회한다. */
export async function fetchSupplierChanges(supplierId: number, page: number): Promise<PageResponse<ChangeItem>> {
  return (await api.get(`/suppliers/${supplierId}/changes`, { params: { page, size: 20 } })).data
}

/** 전체 변경 이력을 대상 종류와 검색어로 검색한다. (관리자 전용) */
export async function searchChanges(search: { targetType?: ChangeTarget; keyword?: string }, page: number): Promise<PageResponse<ChangeItem>> {
  return (await api.get('/admin/changes', {
    params: { targetType: search.targetType, keyword: search.keyword?.trim() || undefined, page, size: 20 },
  })).data
}
