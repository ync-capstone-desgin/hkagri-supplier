import { useMemo, useState } from 'react'
import { App, Button, Flex, Input, Select, Typography } from 'antd'
import { DownloadOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColDef } from 'ag-grid-community'
import { useNavigate } from 'react-router'
import { downloadSupplierExcel, searchSuppliers } from '../../api/suppliers'
import type { SupplierListItem, SupplierSearch } from '../../api/types'
import { useAuth } from '../../app/AuthContext'
import { DataGrid } from '../../components/DataGrid'
import { SearchField, SearchPanel } from '../../components/SearchPanel'
import { SupplierStatusTag } from '../../components/SupplierStatusTag'
import { useCodes } from '../../hooks/useCodes'
import { usePersistedState } from '../../hooks/usePersistedState'
import { SupplierFormModal } from './SupplierFormModal'

/**
 * 협력업체 목록 화면 (FR-001, FR-002).
 * 검색조건 → 그리드 → (더블클릭) 상세 탭 순서로 쓰도록 배치한다.
 * 입력 중인 조건과 마지막으로 조회한 조건은 브라우저에 저장해 탭을 옮겨도 유지한다.
 */
export function SupplierListPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const { canEdit } = useAuth()
  const { options } = useCodes()
  const [draft, setDraft] = usePersistedState<SupplierSearch>('supplier.search.draft', {})
  const [search, setSearch] = usePersistedState<SupplierSearch>('supplier.search', {})
  const [formOpen, setFormOpen] = useState(false)
  const [downloading, setDownloading] = useState(false)

  const { data, isFetching } = useQuery({
    queryKey: ['suppliers', search],
    queryFn: () => searchSuppliers(search),
  })

  /** 그리드 컬럼 정의. 업체명은 클릭하면 바로 상세로 가도록 링크 모양으로 보여준다. */
  const columns = useMemo<ColDef<SupplierListItem>[]>(
    () => [
      { field: 'code', headerName: '업체코드', width: 120, cellClass: 'num' },
      {
        field: 'name',
        headerName: '업체명',
        flex: 1,
        minWidth: 180,
        cellRenderer: (p: { data: SupplierListItem }) => (
          <Typography.Link onClick={() => navigate(`/suppliers/${p.data.id}`)}>{p.data.name}</Typography.Link>
        ),
      },
      {
        field: 'status',
        headerName: '거래상태',
        width: 120,
        cellRenderer: (p: { data: SupplierListItem }) => <SupplierStatusTag status={p.data.status} />,
      },
      { field: 'contactName', headerName: '업체 담당자', width: 140 },
      { field: 'ownerName', headerName: '내부 담당자', width: 140 },
    ],
    [navigate],
  )

  /** 입력한 조건으로 조회한다. */
  const handleSearch = () => setSearch({ ...draft })

  /** 조건을 모두 비우고 전체 목록을 조회한다. */
  const handleReset = () => {
    setDraft({})
    setSearch({})
  }

  /** 마지막으로 조회한 조건의 목록을 엑셀로 내려받는다. */
  const handleExcel = async () => {
    setDownloading(true)
    try {
      await downloadSupplierExcel(search)
    } catch {
      message.error('엑셀 파일을 만들지 못했습니다.')
    } finally {
      setDownloading(false)
    }
  }

  return (
    <>
      <SearchPanel onSearch={handleSearch} onReset={handleReset}>
        <SearchField label="업체명·코드">
          <Input
            allowClear
            style={{ width: 220 }}
            placeholder="예: 가람, SUP-0001"
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
          />
        </SearchField>
        <SearchField label="거래상태">
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="전체"
            options={options('supplierStatus')}
            value={draft.status}
            onChange={(status) => setDraft({ ...draft, status })}
          />
        </SearchField>
      </SearchPanel>

      <Flex justify="space-between" align="center" style={{ marginBottom: 8 }}>
        <Typography.Text>
          총 <b className="num">{data?.totalElements ?? 0}</b>건
          <Typography.Text type="secondary" style={{ marginInlineStart: 12, fontSize: 12 }}>
            행을 더블클릭하면 상세 화면이 새 탭으로 열립니다.
          </Typography.Text>
        </Typography.Text>
        <Flex gap={8}>
          <Button icon={<DownloadOutlined />} onClick={handleExcel} loading={downloading}>
            엑셀 다운로드
          </Button>
          {canEdit && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setFormOpen(true)}>
              업체 등록
            </Button>
          )}
        </Flex>
      </Flex>

      <DataGrid
        rows={data?.content}
        columns={columns}
        loading={isFetching && !data}
        onRowDoubleClick={(row) => navigate(`/suppliers/${row.id}`)}
      />

      <SupplierFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </>
  )
}
