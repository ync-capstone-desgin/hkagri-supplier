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

/** 만료 캘린더 조회 조건: 달력에 보이는 기간과 거르기 조건 */
export interface ExpiryCalendarSearch {
  /** 시작일 (YYYY-MM-DD, 포함) */
  from: string
  /** 종료일 (YYYY-MM-DD, 포함). 시작일부터 최대 62일 */
  to: string
  targetType?: ExpirySearch['targetType']
  ownerId?: number
}

/** 기간 안에 만료되는 계약·인증을 날짜순으로 조회한다. (만료 캘린더용) */
export async function findExpiryCalendar(search: ExpiryCalendarSearch): Promise<ExpiryItem[]> {
  return (await api.get('/expiries/calendar', {
    params: { from: search.from, to: search.to, targetType: search.targetType || undefined, ownerId: search.ownerId },
  })).data
}
