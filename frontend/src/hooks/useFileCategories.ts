import { useQuery } from '@tanstack/react-query'
import { fetchFileCategories } from '../api/files'

/**
 * 자료 종류 목록과 선택상자용 옵션을 제공하는 훅 (#54).
 * options는 사용 중인 종류만 담는다. 사용 중지한 종류는 새로 고를 수 없기 때문이다.
 */
export function useFileCategories() {
  const query = useQuery({ queryKey: ['file-categories'], queryFn: fetchFileCategories, staleTime: 60_000 })
  const options = (query.data ?? []).filter((c) => c.active).map((c) => ({ value: c.id, label: c.name }))
  return { ...query, options }
}
