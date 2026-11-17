import { useState } from 'react'
import { Card, Input, Select } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { searchChanges, type ChangeTarget } from '../../api/changes'
import { SearchField, SearchPanel } from '../../components/SearchPanel'
import { ChangeLogTable } from './ChangeLogTable'

/** 검색 조건: 대상 종류와 검색어 */
interface Search {
  targetType?: ChangeTarget
  keyword: string
}

const EMPTY: Search = { keyword: '' }

/**
 * 전체 변경 이력 화면 (#45, 관리자 전용).
 * 모든 협력업체·계약·인증의 등록·수정·갱신·삭제 기록을 대상 종류와 검색어(업체명·대상 이름·작업자)로 찾는다.
 */
export function ChangeLogPage() {
  const [draft, setDraft] = useState<Search>(EMPTY)
  const [search, setSearch] = useState<Search>(EMPTY)
  const [page, setPage] = useState(0)
  const changes = useQuery({ queryKey: ['changes', 'all', search, page], queryFn: () => searchChanges(search, page) })

  /** 조건을 적용하고 첫 쪽부터 다시 조회한다. */
  const apply = (next: Search) => {
    setDraft(next)
    setSearch(next)
    setPage(0)
  }

  return (
    <>
      <SearchPanel onSearch={() => apply(draft)} onReset={() => apply(EMPTY)}>
        <SearchField label="대상">
          <Select allowClear placeholder="전체" style={{ width: 130 }} value={draft.targetType}
            onChange={(targetType) => setDraft({ ...draft, targetType })}
            options={[{ value: 'SUPPLIER', label: '협력업체' }, { value: 'CONTRACT', label: '계약' }, { value: 'CERTIFICATION', label: '인증' }]} />
        </SearchField>
        <SearchField label="검색어">
          <Input allowClear placeholder="업체명·대상 이름·작업자" style={{ width: 240 }} value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })} />
        </SearchField>
      </SearchPanel>
      <Card>
        <ChangeLogTable data={changes.data} loading={changes.isLoading} page={page} onPageChange={setPage} showSupplier />
      </Card>
    </>
  )
}
