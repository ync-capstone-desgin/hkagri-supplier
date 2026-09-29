import { api, downloadFile } from './client'
import type { ContractItem, ContractRequest, ContractSearch, PageResponse, RenewalStatus, Saved } from './types'

/** 검색 조건에서 빈 값을 빼고 서버 요청 파라미터로 만든다. */
function toParams(search: ContractSearch) {
  return {
    supplierId: search.supplierId,
    keyword: search.keyword?.trim() || undefined,
    ownerId: search.ownerId,
    renewalStatus: search.renewalStatus || undefined,
    includeInactive: search.includeInactive || undefined,
  }
}

/** 계약 목록을 검색한다. 기본 정렬은 종료일이 가까운 순이다. */
export async function searchContracts(search: ContractSearch): Promise<PageResponse<ContractItem>> {
  return (await api.get('/contracts', { params: toParams(search) })).data
}

/** 계약 한 건을 조회한다. */
export async function fetchContract(id: number): Promise<ContractItem> {
  return (await api.get(`/contracts/${id}`)).data
}

/** 계약을 등록한다. */
export async function createContract(req: ContractRequest): Promise<Saved<ContractItem>> {
  return (await api.post('/contracts', req)).data
}

/** 계약 정보를 수정한다. */
export async function updateContract(id: number, req: ContractRequest): Promise<Saved<ContractItem>> {
  return (await api.put(`/contracts/${id}`, req)).data
}

/** 계약을 갱신한다: 새 계약을 등록하고 기존 계약은 이전 이력으로 바뀐다. */
export async function renewContract(id: number, req: ContractRequest): Promise<Saved<ContractItem>> {
  return (await api.post(`/contracts/${id}/renew`, req)).data
}

/** 계약의 갱신 처리상태만 바꾼다. */
export async function changeContractRenewalStatus(id: number, renewalStatus: RenewalStatus): Promise<ContractItem> {
  return (await api.patch(`/contracts/${id}/renewal-status`, { renewalStatus })).data
}

/** 현재 검색조건의 계약 목록을 엑셀로 내려받는다. */
export async function downloadContractExcel(search: ContractSearch): Promise<void> {
  await downloadFile('/contracts/excel', toParams(search))
}
