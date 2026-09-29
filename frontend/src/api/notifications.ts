import { api } from './client'
import type {
  ActionStatus, Channel, DeliveryLogItem, NotificationItem, NotificationRule, PageResponse, ScanRunResult,
} from './types'

/** 내 알림함을 만료일이 가까운 순으로 조회한다. actionStatus를 비우면 전체. */
export async function fetchInbox(actionStatus?: ActionStatus): Promise<PageResponse<NotificationItem>> {
  return (await api.get('/notifications', { params: { actionStatus, size: 200 } })).data
}

/** 내 미확인 알림 수를 조회한다. */
export async function fetchUnreadCount(): Promise<number> {
  return (await api.get<{ count: number }>('/notifications/unread-count')).data.count
}

/** 알림의 처리상태를 바꾼다. */
export async function changeNotificationAction(id: number, actionStatus: ActionStatus): Promise<NotificationItem> {
  return (await api.patch(`/notifications/${id}/action`, { actionStatus })).data
}

/** 내 미확인 알림을 모두 확인 처리한다. */
export async function markAllRead(): Promise<number> {
  return (await api.post<{ updated: number }>('/notifications/read-all')).data.updated
}

/** 알림 기준 목록을 조회한다. */
export async function fetchRules(): Promise<NotificationRule[]> {
  return (await api.get('/settings/notification-rules')).data
}

/** 알림 기준을 바꾼다. (관리자) */
export async function updateRule(id: number, req: { days: number[]; channels: Channel[]; enabled: boolean }): Promise<NotificationRule> {
  return (await api.put(`/settings/notification-rules/${id}`, req)).data
}

/** 최근 발송 이력을 조회한다. (관리자) */
export async function fetchDeliveries(): Promise<DeliveryLogItem[]> {
  return (await api.get('/admin/deliveries')).data
}

/** (시연용·관리자) 만료 점검을 지금 실행하고 바로 발송한다. */
export async function runExpiryScan(): Promise<ScanRunResult> {
  return (await api.post('/admin/jobs/expiry-scan')).data
}
