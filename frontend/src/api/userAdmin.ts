import { api } from './client'
import type { Role } from './types'

/** 관리자 화면에서 사용하는 계정 정보. 비밀번호는 포함하지 않는다. */
export interface AdminUser {
  id: number
  username: string
  name: string
  email: string | null
  role: Role
  status: 'PENDING' | 'ACTIVE' | 'REJECTED' | 'INACTIVE'
  signupReason: string | null
  createdAt: string
}

/** 관리자 계정 작업 종류. */
export type UserAction = 'APPROVE' | 'REJECT' | 'CHANGE_ROLE' | 'DEACTIVATE' | 'REACTIVATE'

/** 계정 목록과 승인 대기 인원을 읽는다. */
export async function fetchAdminUsers(): Promise<{ pendingCount: number; users: AdminUser[] }> {
  return (await api.get('/admin/users')).data
}

/** 선택한 계정에 관리자 작업을 수행한다. */
export async function changeUser(id: number, action: UserAction, role?: Role): Promise<AdminUser> {
  return (await api.post(`/admin/users/${id}/actions`, { action, role })).data
}
