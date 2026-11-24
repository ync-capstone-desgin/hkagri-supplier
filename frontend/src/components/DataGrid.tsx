import { useState, type ReactNode } from 'react'
import { Empty, Flex, Grid, Pagination, Skeleton } from 'antd'
import { AgGridReact } from 'ag-grid-react'
import {
  AllCommunityModule, ModuleRegistry, themeQuartz, type ColDef, type RowClassRules, type RowDoubleClickedEvent,
} from 'ag-grid-community'
import { AG_GRID_LOCALE_KR } from '@ag-grid-community/locale'
import { BRAND } from '../app/theme'

ModuleRegistry.registerModules([AllCommunityModule])

/** 업무 화면용 그리드 테마: 행 높이 36px, 연한 회색 헤더, 브랜드색 강조 */
const gridTheme = themeQuartz.withParams({
  accentColor: BRAND,
  fontFamily: 'inherit',
  fontSize: 13,
  headerFontSize: 13,
  headerFontWeight: 600,
  rowHeight: 36,
  headerHeight: 38,
  headerBackgroundColor: '#f6f6f6',
  wrapperBorderRadius: 6,
  borderColor: '#e5e7eb',
})

/** 휴대폰 카드 목록에서 한 쪽에 보여줄 건수 */
const CARD_PAGE_SIZE = 20

/**
 * AG Grid 공통 래퍼.
 * 한글 메뉴, 정렬·크기조절 기본 적용, 페이지당 50행을 설정하고 행 더블클릭 이벤트를 전달한다.
 * rowClassRules로 조건에 맞는 행에 CSS 클래스를 붙일 수 있다 (예: 이전 이력 행 흐리게).
 * renderCard를 주면 휴대폰 폭(768px 미만)에서는 표 대신 카드 목록으로 보여준다 (FR-023).
 */
export function DataGrid<T extends object>({ rows, columns, loading, onRowDoubleClick, rowClassRules, rowId, paginate = true, height = 560, renderCard, onCardClick, cardClassName }: {
  rows: T[] | undefined
  columns: ColDef<T>[]
  loading?: boolean
  onRowDoubleClick?: (row: T) => void
  rowClassRules?: RowClassRules<T>
  /** 행 식별값. 기본은 id 필드 */
  rowId?: (row: T) => string
  /** 페이지 나누기 표시 여부 (기본 true) */
  paginate?: boolean
  height?: number
  /** 휴대폰에서 한 행을 카드로 그리는 방법. 없으면 휴대폰에서도 표로 보여준다. */
  renderCard?: (row: T) => ReactNode
  /** 휴대폰에서 카드를 눌렀을 때 할 일 (예: 상세로 이동). 없으면 카드 안의 버튼·링크만 동작한다. */
  onCardClick?: (row: T) => void
  /** 휴대폰 카드에 붙일 CSS 클래스 (예: 이전 이력 흐리게) */
  cardClassName?: (row: T) => string | undefined
}) {
  const mobile = Grid.useBreakpoint().md === false
  const [page, setPage] = useState(1)
  const keyOf = (row: T) => (rowId ? rowId(row) : String((row as { id: number }).id))

  if (mobile && renderCard) {
    if (loading) return <Skeleton active />
    if (!rows?.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="조건에 맞는 데이터가 없습니다." />
    // 조건을 바꿔 건수가 줄면 마지막 쪽을 넘지 않게 맞춘다.
    const current = Math.min(page, Math.ceil(rows.length / CARD_PAGE_SIZE))
    const shown = paginate ? rows.slice((current - 1) * CARD_PAGE_SIZE, current * CARD_PAGE_SIZE) : rows
    return (
      <Flex vertical gap={8}>
        {shown.map((row) => (
          <div key={keyOf(row)} className={`data-card ${cardClassName?.(row) ?? ''}`} onClick={onCardClick && (() => onCardClick(row))}
            style={onCardClick ? { cursor: 'pointer' } : undefined}>
            {renderCard(row)}
          </div>
        ))}
        {paginate && rows.length > CARD_PAGE_SIZE && (
          <Pagination simple align="center" current={current} pageSize={CARD_PAGE_SIZE} total={rows.length} onChange={setPage} />
        )}
      </Flex>
    )
  }

  return (
    <div style={{ height }}>
      <AgGridReact<T>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columns}
        loading={loading}
        localeText={AG_GRID_LOCALE_KR}
        getRowId={(p) => keyOf(p.data)}
        defaultColDef={{ sortable: true, resizable: true, minWidth: 80 }}
        pagination={paginate}
        paginationPageSize={50}
        paginationPageSizeSelector={[20, 50, 100]}
        overlayNoRowsTemplate="조건에 맞는 데이터가 없습니다."
        onRowDoubleClicked={(e: RowDoubleClickedEvent<T>) => e.data && onRowDoubleClick?.(e.data)}
        rowStyle={onRowDoubleClick ? { cursor: 'pointer' } : undefined}
        rowClassRules={rowClassRules}
      />
    </div>
  )
}
