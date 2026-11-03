import axios, { AxiosError } from 'axios'
import type { Problem } from './types'

/**
 * 백엔드 API 호출용 axios 인스턴스.
 * 세션 쿠키로 로그인을 유지하므로 같은 출처(/api, Vite 프록시)로 요청한다.
 */
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

/**
 * 오류 객체에서 서버의 ProblemDetail을 꺼낸다.
 * 서버 응답이 없으면(네트워크 오류 등) 기본 안내 문구로 채운다.
 */
export function toProblem(error: unknown): Problem {
  const e = error as AxiosError<Problem>
  if (e.response?.data?.detail) {
    return e.response.data
  }
  return { status: e.response?.status ?? 0, detail: '서버와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요.' }
}

/** 로그인 세션이 끊긴 오류(401)인지 확인한다. */
export function isUnauthorized(error: unknown): boolean {
  return (error as AxiosError).response?.status === 401
}

/**
 * 엑셀 등 파일 응답을 받아 브라우저 다운로드를 실행한다.
 * 파일명은 서버의 Content-Disposition(filename*=UTF-8'') 값을 따른다.
 */
export async function downloadFile(url: string, params?: object): Promise<void> {
  const res = await api.get(url, { params, responseType: 'blob' }).catch(async (error: unknown) => {
    // 파일 요청의 오류 응답도 Blob이므로 기존 오류 안내에서 읽을 수 있게 복원한다.
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      try {
        const problem: unknown = JSON.parse(await error.response.data.text())
        if (problem && typeof problem === 'object' && 'detail' in problem && typeof problem.detail === 'string') {
          error.response.data = problem
        }
      } catch {
        // JSON이 아닌 응답은 원래 오류와 HTTP 상태를 그대로 전달한다.
      }
    }
    throw error
  })
  const disposition: string = res.headers['content-disposition'] ?? ''
  const match = disposition.match(/filename\*=UTF-8''([^;]+)/)
  const fileName = match ? decodeURIComponent(match[1]) : 'download.xlsx'
  const href = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = href
  a.download = fileName
  a.click()
  URL.revokeObjectURL(href)
}
