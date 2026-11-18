import { App, Button, Flex, Popconfirm, Tag, Typography } from 'antd'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { fetchPurgeFileCount, purgeTarget, restoreTarget, type ChangeItem, type FieldChange } from '../../api/changes'
import { toProblem } from '../../api/client'
import { useAuth } from '../../app/AuthContext'
import type { PageResponse } from '../../api/types'
import { formatDateTime } from '../../components/format'
import { ResponsiveTable } from '../../components/ResponsiveTable'

/** 작업 종류별 태그 색 */
const ACTION_COLOR: Record<ChangeItem['action'], string> = {
  CREATE: 'green', UPDATE: 'blue', RENEW: 'cyan', STATUS: 'gold', DELETE: 'red', RESTORE: 'purple', PURGE: 'magenta',
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
 * 관리자에게는 아직 처리하지 않은 삭제 기록 옆에 되살리기(#72)와 영구삭제(#77) 버튼을 보여 준다.
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
  const { me } = useAuth()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()

  /** 삭제한 대상을 되살리고, 목록·건수·이력이 모두 달라지므로 받아 둔 데이터를 전부 새로 받는다. */
  const restore = async (row: ChangeItem) => {
    try {
      await restoreTarget(row.targetType, row.targetId)
      message.success(`${row.targetLabel} "${row.targetName}"을(를) 되살렸습니다.`)
      await queryClient.invalidateQueries()
    } catch (cause) { message.error(toProblem(cause).detail) }
  }

  /** 함께 없어질 파일 수를 보여 주고 한 번 더 확인받은 뒤 영구삭제한다. 실패하면 확인 창을 유지한다. */
  const confirmPurge = async (row: ChangeItem) => {
    let files: number
    try { files = await fetchPurgeFileCount(row.targetType, row.targetId) }
    catch (cause) { message.error(toProblem(cause).detail); return }
    modal.confirm({
      title: '영구삭제하시겠습니까?',
      content: <>
        <div>{row.targetLabel} "{row.targetName}"</div>
        <div>첨부 파일 {files}개{row.targetType === 'SUPPLIER' && '와 담당자·메모'}도 함께 지워집니다.</div>
        <Typography.Text type="danger" strong>영구삭제하면 되돌릴 수 없습니다.</Typography.Text>
      </>,
      okText: '영구삭제', cancelText: '취소', okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await purgeTarget(row.targetType, row.targetId)
          message.success(`${row.targetLabel} "${row.targetName}"을(를) 영구삭제했습니다.`)
          await queryClient.invalidateQueries()
        } catch (cause) { message.error(toProblem(cause).detail); throw cause }
      },
    })
  }

  return (
    <ResponsiveTable<ChangeItem> rowKey="id" dataSource={data?.content} loading={loading} size="small" scroll={{ x: 1080 }}
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
        ...(me?.role === 'ADMIN' ? [{
          title: '삭제한 항목 처리', width: 180,
          render: (_: unknown, row: ChangeItem) => row.restorable && (
            <Flex gap={4}>
              <Popconfirm title="되살리시겠습니까?" description="함께 지워진 첨부 파일도 다시 보입니다." okText="되살리기" cancelText="취소" onConfirm={() => restore(row)}>
                <Button size="small" aria-label={`되살리기: ${row.targetName}`}>되살리기</Button>
              </Popconfirm>
              <Button size="small" danger aria-label={`영구삭제: ${row.targetName}`} onClick={() => confirmPurge(row)}>영구삭제</Button>
            </Flex>
          ),
        }] : []),
      ]} />
  )
}
