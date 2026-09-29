import { useEffect, useState } from 'react'

/**
 * localStorage에 저장되는 상태 훅.
 * 검색조건처럼 탭을 옮기거나 새로고침해도 유지되면 편한 값에 쓴다.
 * 개인 브라우저 편의 기능이므로 저장소를 쓸 수 없으면(사생활 보호 모드 등) 조용히 기본값으로 동작한다.
 */
export function usePersistedState<T>(key: string, initial: T) {
  const [value, setValue] = useState<T>(() => {
    try {
      const saved = localStorage.getItem(key)
      return saved ? (JSON.parse(saved) as T) : initial
    } catch {
      return initial
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value))
    } catch {
      // 저장 실패는 무시한다 (편의 기능)
    }
  }, [key, value])

  return [value, setValue] as const
}
