import { useState, type ReactNode } from 'react'
import { Empty, Flex, Grid, Pagination, Skeleton, Table, type TableProps } from 'antd'
import type { ColumnType } from 'antd/es/table'
import { DataList } from './DataList'

/** 쪽 나누기 설정에 한 쪽 건수가 없을 때 쓰는 값 (antd 표의 기본값과 같다) */
const DEFAULT_PAGE_SIZE = 10

/** 열의 dataIndex(이름 또는 경로)로 행에서 값을 꺼낸다. */
function cellValue<T>(row: T, dataIndex: ColumnType<T>['dataIndex']): unknown {
  if (dataIndex == null) return undefined
  const path = (Array.isArray(dataIndex) ? dataIndex : [dataIndex]) as (string | number)[]
  return path.reduce<unknown>((value, key) => (value as Record<string | number, unknown> | null | undefined)?.[key], row)
}

/**
 * 휴대폰에서는 카드 목록으로 바뀌는 표 (FR-023).
 * PC 폭에서는 antd 표를 그대로 그리고, 휴대폰 폭(768px 미만)에서는 같은 열 정의로 행마다 카드 한 장을 그린다.
 * 카드의 첫 줄은 첫 열 값을 굵게, 나머지 열은 "열 이름 + 값"으로 한 줄씩 보여준다. 값이 없는 열은 뺀다.
 * mobileRow를 주면 카드 대신 그 내용을 한 줄씩 그어 나눈 목록으로 보여준다 (파일 목록처럼 촘촘하게 보여야 하는 화면용).
 * 지원 범위: 열의 title·dataIndex·render, rowKey, loading, locale.emptyText, pagination(false·서버 쪽 나누기·화면 쪽 나누기).
 */
export function ResponsiveTable<T extends object>({ mobileRow, ...props }: TableProps<T> & {
  /** 휴대폰에서 한 행을 그리는 방법. 주면 열 정의 대신 이 내용으로 목록을 만든다. */
  mobileRow?: (row: T) => ReactNode
}) {
  const mobile = Grid.useBreakpoint().md === false
  const [localPage, setLocalPage] = useState(1)
  if (!mobile) return <Table<T> {...props} />

  const { dataSource, rowKey, loading, locale, pagination } = props
  const columns = (props.columns ?? []) as ColumnType<T>[]
  const rows = dataSource ?? []
  if (loading) return <Skeleton active />
  if (!rows.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={(locale?.emptyText as ReactNode) ?? '데이터가 없습니다.'} />

  // current가 있으면 서버가 쪽을 나눈 것이고, 없으면 받은 행을 화면에서 나눈다.
  const paging = pagination === false ? undefined : pagination ?? {}
  const pageSize = paging?.pageSize ?? DEFAULT_PAGE_SIZE
  const server = paging?.current != null
  const total = paging?.total ?? rows.length
  const current = server ? paging!.current! : Math.min(localPage, Math.ceil(rows.length / pageSize))
  const shown = paging && !server ? rows.slice((current - 1) * pageSize, current * pageSize) : rows
  const keyOf = (row: T, index: number) =>
    String(typeof rowKey === 'function' ? rowKey(row, index) : (row as Record<string, unknown>)[(rowKey as string) ?? 'key'])

  return (
    <Flex vertical gap={8}>
      {mobileRow && <DataList rows={shown} rowKey={keyOf} renderRow={mobileRow} />}
      {!mobileRow && shown.map((row, index) => (
        <div key={keyOf(row, index)} className="data-card">
          {columns.map((column, i) => {
            const value = cellValue(row, column.dataIndex)
            const content = (column.render ? column.render(value, row, index) : value) as ReactNode
            // 값이 없는 줄(예: 처리할 것이 없는 작업 칸)은 이름만 남지 않게 뺀다.
            if (i > 0 && (content == null || content === false || content === '')) return null
            return i === 0
              ? <div key={i} className="card-title">{content}</div>
              : (
                <div key={i} className="card-row">
                  <span className="card-label">{column.title as ReactNode}</span>
                  <div className={`card-value ${column.className ?? ''}`}>{content}</div>
                </div>
              )
          })}
        </div>
      ))}
      {paging && total > pageSize && (
        <Pagination simple align="center" current={current} pageSize={pageSize} total={total}
          onChange={(page) => (server ? paging.onChange?.(page, pageSize) : setLocalPage(page))} />
      )}
    </Flex>
  )
}
