import { Table, Tag, Typography } from 'antd'
import { useNavigate } from 'react-router'
import type { ChangeItem, FieldChange } from '../../api/changes'
import type { PageResponse } from '../../api/types'
import { formatDateTime } from '../../components/format'

/** 작업 종류별 태그 색 */
const ACTION_COLOR: Record<ChangeItem['action'], string> = {
  CREATE: 'green', UPDATE: 'blue', RENEW: 'cyan', STATUS: 'gold', DELETE: 'red',
}

/** 바뀐 항목 한 줄: "종료일: 2026-12-31 → 2027-12-31". 등록처럼 이전 값이 없으면 바뀐 값만 보여 준다. */
function ChangeLine({ change }: { change: FieldChange }) {
  return (
    <div style={{ overflowWrap: 'anywhere' }}>
      <Typography.Text type="secondary">{change.label}: </Typography.Text>
      {change.before !== null && <><Typography.Text delete type="secondary">{change.before}</Typography.Text> → </>}
      {change.after ?? <Typography.Text type="secondary">(비움)</Typography.Text>}
    </div>
  )
}

/**
 * 변경 이력 표 (#45). 업체 상세의 변경 이력 탭과 관리자용 전체 이력 화면에서 함께 쓴다.
 * 누가 언제 어떤 대상에 무엇을 했는지와, 항목별로 무엇이 무엇으로 바뀌었는지를 보여 준다.
 */
export function ChangeLogTable({ data, loading, page, onPageChange, showSupplier = false }: {
  data: PageResponse<ChangeItem> | undefined
  loading: boolean
  page: number
  onPageChange: (page: number) => void
  /** 전체 이력 화면에서는 업체명 열을 보여 준다. */
  showSupplier?: boolean
}) {
  const navigate = useNavigate()
  return (
    <Table<ChangeItem> rowKey="id" dataSource={data?.content} loading={loading} size="small" scroll={{ x: 900 }}
      locale={{ emptyText: '변경 이력이 없습니다.' }}
      pagination={{ current: page + 1, pageSize: 20, total: data?.totalElements ?? 0, showSizeChanger: false, onChange: (next) => onPageChange(next - 1) }}
      columns={[
        { title: '일시', dataIndex: 'createdAt', width: 150, render: formatDateTime },
        { title: '작업자', width: 140, render: (_, row) => row.actorName === row.actor ? row.actor : `${row.actorName} (${row.actor})` },
        ...(showSupplier ? [{
          title: '업체', width: 170,
          render: (_: unknown, row: ChangeItem) => <Typography.Link onClick={() => navigate(`/suppliers/${row.supplierId}`)}>{row.supplierName}</Typography.Link>,
        }] : []),
        { title: '대상', width: 240, render: (_, row) => <span style={{ overflowWrap: 'anywhere' }}><Tag>{row.targetLabel}</Tag>{row.targetName}</span> },
        { title: '작업', width: 120, render: (_, row) => <Tag color={ACTION_COLOR[row.action]}>{row.actionLabel}</Tag> },
        { title: '변경 내용', render: (_, row) => row.changes.length > 0
          ? row.changes.map((change) => <ChangeLine key={change.label} change={change} />)
          : <Typography.Text type="secondary">-</Typography.Text> },
      ]} />
  )
}
