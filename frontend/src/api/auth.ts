import { api } from './client'
import type { Me, UserOption } from './types'

/** 아이디·비밀번호로 로그인하고 로그인한 사용자 정보를 받는다. */
export async function login(username: string, password: string): Promise<Me> {
  return (await api.post<Me>('/auth/login', { username, password })).data
}

/** 로그아웃하고 서버 세션을 끝낸다. */
export async function logout(): Promise<void> {
  await api.post('/auth/logout')
}

/** 현재 로그인한 사용자 정보를 조회한다. 로그인 전이면 401 오류가 난다. */
export async function fetchMe(): Promise<Me> {
  return (await api.get<Me>('/auth/me')).data
}

/** 담당자 선택상자에 쓸 사용자 목록을 조회한다. */
export async function fetchUsers(): Promise<UserOption[]> {
  return (await api.get<UserOption[]>('/users')).data
}
