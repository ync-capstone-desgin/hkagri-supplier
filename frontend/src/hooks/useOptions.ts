import { useQuery } from '@tanstack/react-query'
import { fetchUsers } from '../api/auth'
import { fetchCertTypes } from '../api/certifications'
import { searchSuppliers } from '../api/suppliers'

/** 활성 담당자 선택 목록. 기존 비활성·조회자 담당자는 이름만 표시하고 재선택은 막는다. */
export function useOwnerOptions(enabled = true, current?: { ownerId: number | null; ownerName: string | null }) {
  const { data } = useQuery({ queryKey: ['users'], queryFn: fetchUsers, enabled, staleTime: 5 * 60_000 })
  const options: { value: number; label: string; disabled?: boolean }[] = (data ?? [])
    .filter((u) => u.role !== 'VIEWER').map((u) => ({ value: u.id, label: u.name }))
  if (current?.ownerId != null && !options.some((o) => o.value === current.ownerId)) {
    const activeUser = data?.find((u) => u.id === current.ownerId)
    const suffix = data ? (activeUser ? ' (조회자)' : ' (비활성)') : ''
    options.push({ value: current.ownerId, label: `${current.ownerName ?? '기존 담당자'}${suffix}`, disabled: true })
  }
  return options
}

/** 인증 종류 선택상자 옵션 */
export function useCertTypeOptions() {
  const { data } = useQuery({ queryKey: ['cert-types'], queryFn: fetchCertTypes, staleTime: Infinity })
  return (data ?? []).map((t) => ({ value: t.code, label: t.name }))
}

/**
 * 협력업체 선택상자 옵션. 이름으로 검색할 수 있도록 업체코드를 함께 보여준다.
 * 선택 시 업체의 내부 담당자를 기본 담당자로 쓰기 위해 ownerId도 함께 돌려준다.
 */
export function useSupplierOptions(enabled = true) {
  const { data } = useQuery({ queryKey: ['suppliers', 'options'], queryFn: () => searchSuppliers({}), enabled })
  return (data?.content ?? []).map((s) => ({ value: s.id, label: `${s.name} (${s.code})`, ownerId: s.ownerId }))
}
