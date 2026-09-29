import { api, downloadFile } from './client'
import type { ExpiryItem, ExpirySearch } from './types'

/** 검색 조건에서 빈 값을 빼고 서버 요청 파라미터로 만든다. */
function toParams(search: ExpirySearch) {
  return {
    period: search.period,
    targetType: search.targetType || undefined,
    ownerId: search.ownerId,
    renewalStatus: search.renewalStatus || undefined,
  }
}

/** 만료 예정 목록을 조회한다. 급한 순(만료 → D-0 → …)으로 정렬되어 온다. */
export async function findExpiries(search: ExpirySearch): Promise<ExpiryItem[]> {
  return (await api.get('/expiries', { params: toParams(search) })).data
}

/** 현재 조건의 만료 예정 목록을 엑셀로 내려받는다. */
export async function downloadExpiryExcel(search: ExpirySearch): Promise<void> {
  await downloadFile('/expiries/excel', toParams(search))
}
