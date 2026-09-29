import { useMemo, useState } from 'react'
import { App, Button, Flex, Tag, Tooltip, Typography } from 'antd'
import { EditOutlined, LinkOutlined, SyncOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import type { ColDef } from 'ag-grid-community'
import { useNavigate } from 'react-router'
import { changeContractRenewalStatus } from '../../api/contracts'
import { toProblem } from '../../api/client'
import type { ContractItem, RenewalStatus } from '../../api/types'
import { useAuth } from '../../app/AuthContext'
import { DataGrid } from '../../components/DataGrid'
import { DdayBadge } from '../../components/DdayBadge'
import { RenewalStatusSelect, RenewalStatusTag } from '../../components/RenewalStatusTag'
import { ContractFormModal, type ContractFormMode } from './ContractFormModal'

/**
 * 계약 그리드 (계약 관리 화면, 업체 상세의 계약 탭에서 함께 사용).
 * 행마다 [수정]·[갱신] 버튼과 갱신 처리상태 선택상자를 두어 목록에서 바로 처리할 수 있게 한다.
 * 갱신되어 이전 이력이 된 계약은 흐리게 "이전" 표시를 붙이고 수정·갱신을 막는다.
 */
export function ContractGrid({ rows, loading, showSupplier = true, height }: {
  rows: ContractItem[] | undefined
  loading?: boolean
  /** 업체 상세 탭에서는 업체명 컬럼을 숨긴다. */
  showSupplier?: boolean
  height?: number
}) {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { canEdit } = useAuth()
  const [modal, setModal] = useState<{ mode: ContractFormMode; contract: ContractItem }>()

  /** 목록에서 갱신 처리상태를 바꾸고 관련 목록을 새로 고친다. */
  const handleStatusChange = async (row: ContractItem, status: RenewalStatus) => {
    try {
      await changeContractRenewalStatus(row.id, status)
      await Promise.all(['contracts', 'expiries', 'dashboard'].map((key) => queryClient.invalidateQueries({ queryKey: [key] })))
      message.success(`${row.contractNumber} 처리상태를 변경했습니다.`)
    } catch (e) {
      message.error(toProblem(e).detail)
    }
  }

  /** 그리드 컬럼 정의. 날짜·번호는 고정폭 숫자, 무기한은 글자로 표시한다. */
  const columns = useMemo<ColDef<ContractItem>[]>(() => [
    ...(showSupplier ? [{
      field: 'supplierName', headerName: '업체명', width: 150,
      cellRenderer: (p: { data: ContractItem }) => (
        <Typography.Link onClick={() => navigate(`/suppliers/${p.data.supplierId}`)}>{p.data.supplierName}</Typography.Link>
      ),
    } as ColDef<ContractItem>] : []),
    { field: 'contractNumber', headerName: '계약번호', width: 150, cellClass: 'num' },
    {
      field: 'title', headerName: '계약명', flex: 1, minWidth: 180,
      cellRenderer: (p: { data: ContractItem }) => (
        <span>
          {!p.data.active && <Tag style={{ marginInlineEnd: 6 }}>이전</Tag>}
          {p.data.title}
          {p.data.documentUrl && (
            <Tooltip title="계약서 열기">
              <a href={p.data.documentUrl} target="_blank" rel="noreferrer" style={{ marginInlineStart: 6 }}>
                <LinkOutlined />
              </a>
            </Tooltip>
          )}
        </span>
      ),
    },
    { field: 'startDate', headerName: '시작일', width: 115, cellClass: 'num' },
    { field: 'endDate', headerName: '종료일', width: 115, cellClass: 'num', valueFormatter: (p) => p.value ?? '무기한' },
    {
      field: 'daysLeft', headerName: 'D-day', width: 130,
      cellRenderer: (p: { data: ContractItem }) => p.data.active
        ? <DdayBadge daysLeft={p.data.daysLeft} status={p.data.expiryStatus} />
        : null,
    },
    { field: 'autoRenew', headerName: '자동연장', width: 95, cellDataType: false, valueFormatter: (p) => (p.value ? '예' : '-') },
    {
      field: 'renewalStatus', headerName: '갱신 처리', width: 160,
      cellRenderer: (p: { data: ContractItem }) =>
        canEdit && p.data.active
          ? <RenewalStatusSelect value={p.data.renewalStatus} onChange={(s) => handleStatusChange(p.data, s)} />
          : <RenewalStatusTag status={p.data.renewalStatus} />,
    },
    { field: 'ownerName', headerName: '담당자', width: 100 },
    ...(canEdit ? [{
      headerName: '작업', width: 150, sortable: false, pinned: 'right',
      cellRenderer: (p: { data: ContractItem }) => p.data.active && (
        <Flex gap={4}>
          <Button size="small" icon={<EditOutlined />} onClick={() => setModal({ mode: 'edit', contract: p.data })}>수정</Button>
          <Button size="small" icon={<SyncOutlined />} onClick={() => setModal({ mode: 'renew', contract: p.data })}>갱신</Button>
        </Flex>
      ),
    } as ColDef<ContractItem>] : []),
  ], [showSupplier, canEdit, navigate])

  return (
    <>
      <DataGrid rows={rows} columns={columns} loading={loading} height={height}
        rowClassRules={{ 'row-inactive': (p) => p.data?.active === false }}
        onRowDoubleClick={canEdit ? (row) => row.active && setModal({ mode: 'edit', contract: row }) : undefined} />
      <ContractFormModal open={!!modal} mode={modal?.mode ?? 'edit'} contract={modal?.contract} onClose={() => setModal(undefined)} />
    </>
  )
}
