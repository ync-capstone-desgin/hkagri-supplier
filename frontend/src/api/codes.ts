import { api } from './client'
import type { Codes } from './types'

/** 화면에서 쓰는 공통 코드(상태·역할 등의 한글 이름)를 조회한다. */
export async function fetchCodes(): Promise<Codes> {
  return (await api.get<Codes>('/codes')).data
}
