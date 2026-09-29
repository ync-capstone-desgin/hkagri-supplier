import { useQuery } from '@tanstack/react-query'
import { fetchCodes } from '../api/codes'

/**
 * 공통 코드 조회 훅.
 * 코드는 거의 바뀌지 않으므로 한 번 받은 값을 계속 재사용한다.
 */
export function useCodes() {
  const { data } = useQuery({ queryKey: ['codes'], queryFn: fetchCodes, staleTime: Infinity })

  /** 코드 그룹의 선택상자 옵션({value, label}) 목록을 만든다. */
  const options = (group: string) => (data?.[group] ?? []).map((c) => ({ value: c.code, label: c.label }))

  /** 코드값의 한글 이름을 찾는다. 없으면 코드값을 그대로 보여준다. */
  const label = (group: string, code?: string | null) =>
    data?.[group]?.find((c) => c.code === code)?.label ?? code ?? ''

  return { options, label }
}
