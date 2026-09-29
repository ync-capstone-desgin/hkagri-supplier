import { api } from './client'
import type { ClockInfo, DashboardSummary } from './types'

/** 로그인 사용자 기준 대시보드 요약을 조회한다. */
export async function fetchDashboard(): Promise<DashboardSummary> {
  return (await api.get('/dashboard/summary')).data
}

/** 현재 기준일 정보를 조회한다. */
export async function fetchClock(): Promise<ClockInfo> {
  return (await api.get('/system/clock')).data
}

/** (시연용·관리자) 기준일을 옮긴다. null이면 실제 날짜로 되돌린다. */
export async function setClock(date: string | null): Promise<ClockInfo> {
  return (await api.put('/admin/clock', { date })).data
}
