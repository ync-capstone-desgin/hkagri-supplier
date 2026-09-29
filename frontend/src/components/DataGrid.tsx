import { AgGridReact } from 'ag-grid-react'
import { AllCommunityModule, ModuleRegistry, themeQuartz, type ColDef, type RowDoubleClickedEvent } from 'ag-grid-community'
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
  headerBackgroundColor: '#f5f7f6',
  wrapperBorderRadius: 6,
  borderColor: '#e5e7eb',
})

/**
 * AG Grid 공통 래퍼.
 * 한글 메뉴, 정렬·크기조절 기본 적용, 페이지당 50행을 설정하고 행 더블클릭 이벤트를 전달한다.
 */
export function DataGrid<T extends { id: number }>({ rows, columns, loading, onRowDoubleClick, height = 560 }: {
  rows: T[] | undefined
  columns: ColDef<T>[]
  loading?: boolean
  onRowDoubleClick?: (row: T) => void
  height?: number
}) {
  return (
    <div style={{ height }}>
      <AgGridReact<T>
        theme={gridTheme}
        rowData={rows}
        columnDefs={columns}
        loading={loading}
        localeText={AG_GRID_LOCALE_KR}
        getRowId={(p) => String(p.data.id)}
        defaultColDef={{ sortable: true, resizable: true, minWidth: 80 }}
        pagination
        paginationPageSize={50}
        paginationPageSizeSelector={[20, 50, 100]}
        overlayNoRowsTemplate="조건에 맞는 데이터가 없습니다."
        onRowDoubleClicked={(e: RowDoubleClickedEvent<T>) => e.data && onRowDoubleClick?.(e.data)}
        rowStyle={onRowDoubleClick ? { cursor: 'pointer' } : undefined}
      />
    </div>
  )
}
