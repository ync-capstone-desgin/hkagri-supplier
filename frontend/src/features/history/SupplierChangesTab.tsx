import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchSupplierChanges } from '../../api/changes'
import { ChangeLogTable } from './ChangeLogTable'

/** 업체 상세의 변경 이력 탭 (#45): 이 업체와 이 업체의 계약·인증 이력을 최신순으로 보여 준다. */
export function SupplierChangesTab({ supplierId }: { supplierId: number }) {
  const [page, setPage] = useState(0)
  const changes = useQuery({ queryKey: ['changes', supplierId, page], queryFn: () => fetchSupplierChanges(supplierId, page) })
  return <ChangeLogTable data={changes.data} loading={changes.isLoading} page={page} onPageChange={setPage} />
}
