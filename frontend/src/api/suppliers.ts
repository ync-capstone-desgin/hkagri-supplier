import { api, downloadFile } from './client'
import type { PageResponse, SupplierDetail, SupplierListItem, SupplierRequest, SupplierSearch } from './types'

/** 검색 조건에서 빈 값을 빼고 서버 요청 파라미터로 만든다. */
function toParams(search: SupplierSearch) {
  return {
    keyword: search.keyword?.trim() || undefined,
    status: search.status || undefined,
    certType: search.certType || undefined,
  }
}

/**
 * 협력업체 목록을 검색한다.
 * 프로토타입 규모(수백 건 이하)를 가정해 한 번에 최대 500건을 받아 그리드에서 페이지를 나눈다.
 */
export async function searchSuppliers(search: SupplierSearch): Promise<PageResponse<SupplierListItem>> {
  return (await api.get('/suppliers', { params: { ...toParams(search), size: 500 } })).data
}

/** 협력업체 상세정보를 조회한다. */
export async function fetchSupplier(id: number): Promise<SupplierDetail> {
  return (await api.get(`/suppliers/${id}`)).data
}

/** 협력업체를 등록한다. */
export async function createSupplier(req: SupplierRequest): Promise<SupplierDetail> {
  return (await api.post('/suppliers', req)).data
}

/** 협력업체 기본정보를 수정한다. */
export async function updateSupplier(id: number, req: SupplierRequest): Promise<SupplierDetail> {
  return (await api.put(`/suppliers/${id}`, req)).data
}

/** 현재 검색조건의 협력업체 목록을 엑셀로 내려받는다. */
export async function downloadSupplierExcel(search: SupplierSearch): Promise<void> {
  await downloadFile('/suppliers/excel', toParams(search))
}

/** 사업자번호 확인 결과: 검증번호가 맞는지와, 같은 번호로 이미 등록된 업체 */
export interface BusinessNumberCheck {
  valid: boolean
  duplicates: { id: number; code: string; name: string; status: SupplierDetail['status'] }[]
}

/** 사업자번호를 확인한다. 수정 중이면 자기 자신(exceptId)은 중복에서 뺀다. */
export async function checkBusinessNumber(number: string, exceptId?: number): Promise<BusinessNumberCheck> {
  return (await api.get('/suppliers/business-number-check', { params: { number, exceptId } })).data
}
