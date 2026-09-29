import { api, downloadFile } from './client'
import type {
  CertificationItem, CertificationRequest, CertificationSearch, CertType, PageResponse, RenewalStatus, Saved,
} from './types'

/** 검색 조건에서 빈 값을 빼고 서버 요청 파라미터로 만든다. */
function toParams(search: CertificationSearch) {
  return {
    supplierId: search.supplierId,
    keyword: search.keyword?.trim() || undefined,
    certType: search.certType || undefined,
    ownerId: search.ownerId,
    renewalStatus: search.renewalStatus || undefined,
    includeInactive: search.includeInactive || undefined,
  }
}

/** 인증 종류 목록을 조회한다. */
export async function fetchCertTypes(): Promise<CertType[]> {
  return (await api.get('/cert-types')).data
}

/** 인증 목록을 검색한다. 기본 정렬은 만료일이 가까운 순이다. */
export async function searchCertifications(search: CertificationSearch): Promise<PageResponse<CertificationItem>> {
  return (await api.get('/certifications', { params: toParams(search) })).data
}

/** 인증을 등록한다. */
export async function createCertification(req: CertificationRequest): Promise<Saved<CertificationItem>> {
  return (await api.post('/certifications', req)).data
}

/** 인증 정보를 수정한다. */
export async function updateCertification(id: number, req: CertificationRequest): Promise<Saved<CertificationItem>> {
  return (await api.put(`/certifications/${id}`, req)).data
}

/** 인증을 갱신한다: 새 인증을 등록하고 기존 인증은 이전 이력으로 바뀐다. */
export async function renewCertification(id: number, req: CertificationRequest): Promise<Saved<CertificationItem>> {
  return (await api.post(`/certifications/${id}/renew`, req)).data
}

/** 인증의 갱신 처리상태만 바꾼다. */
export async function changeCertificationRenewalStatus(id: number, renewalStatus: RenewalStatus): Promise<CertificationItem> {
  return (await api.patch(`/certifications/${id}/renewal-status`, { renewalStatus })).data
}

/** 현재 검색조건의 인증 목록을 엑셀로 내려받는다. */
export async function downloadCertificationExcel(search: CertificationSearch): Promise<void> {
  await downloadFile('/certifications/excel', toParams(search))
}
