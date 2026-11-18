import type { ReactNode } from 'react'
import { Empty, Skeleton } from 'antd'

/**
 * 한 상자 안에 줄을 그어 행을 나눈 촘촘한 목록 (파일 탐색기 모양).
 * 양식 자료실은 PC·휴대폰 모두 이 목록을 쓰고, ResponsiveTable은 휴대폰에서 mobileRow가 있을 때 쓴다.
 * 한 행의 내용은 renderRow가 그리며, 보통 data-list-item 클래스로 아이콘·이름·부가 정보를 한 줄에 둔다.
 */
export function DataList<T>({ rows, rowKey, renderRow, loading, emptyText = '데이터가 없습니다.' }: {
  rows: readonly T[] | undefined
  rowKey: (row: T, index: number) => string
  renderRow: (row: T) => ReactNode
  loading?: boolean
  emptyText?: ReactNode
}) {
  if (loading) return <Skeleton active />
  if (!rows?.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />
  return (
    <div className="data-list">
      {rows.map((row, index) => <div key={rowKey(row, index)} className="data-list-row">{renderRow(row)}</div>)}
    </div>
  )
}
