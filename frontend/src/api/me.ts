import { api } from './client'
import type { MyProfile, PasswordChangeRequest, ProfileUpdateRequest } from './types'

/** 내 정보를 조회한다. */
export async function fetchMyProfile(): Promise<MyProfile> {
  return (await api.get<MyProfile>('/me/profile')).data
}

/** 이름·이메일·알림메일 수신 여부를 수정한다. */
export async function updateMyProfile(req: ProfileUpdateRequest): Promise<MyProfile> {
  return (await api.put<MyProfile>('/me/profile', req)).data
}

/** 현재 비밀번호를 확인한 뒤 새 비밀번호로 바꾼다. 틀리면 오류가 올라온다. */
export async function changeMyPassword(req: PasswordChangeRequest): Promise<void> {
  await api.put('/me/password', req)
}
