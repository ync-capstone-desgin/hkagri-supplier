import { useEffect, useMemo, useState } from 'react'
import { App, Button, Checkbox, Flex, Input, Select, Tag, Tooltip, Typography } from 'antd'
import { DownloadOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColDef } from 'ag-grid-community'
import { useNavigate, useSearchParams } from 'react-router'
import { downloadSupplierExcel, searchSuppliers } from '../../api/suppliers'
import type { SupplierListItem, SupplierSearch } from '../../api/types'
import { useAuth } from '../../app/AuthContext'
import { DataGrid } from '../../components/DataGrid'
import { SearchField, SearchPanel } from '../../components/SearchPanel'
import { SupplierStatusTag } from '../../components/SupplierStatusTag'
import { useCodes } from '../../hooks/useCodes'
import { useCertTypeOptions } from '../../hooks/useOptions'
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
  const certTypeOptions = useCertTypeOptions()
  const [draft, setDraft] = usePersistedState<SupplierSearch>('supplier.search.draft', {})
  const [search, setSearch] = usePersistedState<SupplierSearch>('supplier.search', {})
  const [formOpen, setFormOpen] = useState(false)
  const [downloading, setDownloading] = useState(false)

  // 대시보드의 "인증 만료 N곳"에서 넘어오면(?certExpired=1) 그 조건만 적용해 조회하고 주소에서 표시를 지운다.
  const [params, setParams] = useSearchParams()
  useEffect(() => {
    if (params.get('certExpired')) {
      setDraft({ certExpired: true })
      setSearch({ certExpired: true })
      setParams({}, { replace: true })
    }
  }, [params, setParams, setDraft, setSearch])

  const { data, isFetching } = useQuery({
    queryKey: ['suppliers', search],
    queryFn: () => searchSuppliers(search),
  })

  /**
   * 그리드 컬럼 정의. 업체명은 클릭하면 바로 상세로 가도록 링크 모양으로 보여준다.
   * 만료·임박 건수가 있으면 빨간 숫자로 강조해 먼저 눈에 띄게 한다.
   */
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
        width: 180,
        cellRenderer: (p: { data: SupplierListItem }) => (
          <>
            <SupplierStatusTag status={p.data.status} />
            {p.data.expiredRequiredCertCount > 0 && (
              <Tooltip title={`필수 인증 ${p.data.expiredRequiredCertCount}건이 만료됐습니다`}><Tag color="red">인증 만료</Tag></Tooltip>
            )}
          </>
        ),
      },
      { field: 'contactName', headerName: '업체 담당자', width: 140 },
      { field: 'ownerName', headerName: '내부 담당자', width: 130 },
      { field: 'contractCount', headerName: '계약', width: 90, cellClass: 'num ag-right-aligned-cell', type: 'rightAligned' },
      { field: 'certCount', headerName: '인증', width: 90, cellClass: 'num ag-right-aligned-cell', type: 'rightAligned' },
      {
        field: 'expiringCount',
        headerName: '만료·임박',
        headerTooltip: '이미 만료됐거나 90일 안에 만료되는 계약·인증 수',
        width: 110,
        type: 'rightAligned',
        cellRenderer: (p: { value: number }) =>
          p.value > 0 ? (
            <Tooltip title="만료됐거나 90일 안에 만료">
              <Typography.Text type="danger" strong className="num">{p.value}</Typography.Text>
            </Tooltip>
          ) : (
            <Typography.Text type="secondary" className="num">0</Typography.Text>
          ),
      },
      {
        field: 'missingDocCount',
        headerName: '서류 누락',
        headerTooltip: '관리자가 필수로 정한 자료 종류 중 이 업체에 파일이 없는 종류 수',
        width: 110,
        type: 'rightAligned',
        cellRenderer: (p: { value: number }) =>
          p.value > 0
            ? <Typography.Text type="danger" strong className="num">{p.value}</Typography.Text>
            : <Typography.Text type="secondary" className="num">0</Typography.Text>,
      },
    ],
    [navigate],
  )

  /** 휴대폰 카드 한 장: 업체명·거래상태, 코드·담당자, 계약·인증·만료 임박·서류 누락 건수 순으로 보여준다. */
  const renderCard = (row: SupplierListItem) => (
    <>
      <Flex align="center" gap={6} wrap>
        <Typography.Link strong onClick={() => navigate(`/suppliers/${row.id}`)}>{row.name}</Typography.Link>
        <SupplierStatusTag status={row.status} />
        {row.expiredRequiredCertCount > 0 && <Tag color="red">인증 만료</Tag>}
      </Flex>
      <Typography.Text type="secondary" style={{ display: 'block', margin: '4px 0', fontSize: 12 }}>
        <span className="num">{row.code}</span> · 업체 담당 {row.contactName ?? '-'} · 내부 담당 {row.ownerName ?? '미지정'}
      </Typography.Text>
      <Flex gap={12} wrap className="num">
        <span>계약 {row.contractCount}</span>
        <span>인증 {row.certCount}</span>
        <Typography.Text type={row.expiringCount > 0 ? 'danger' : 'secondary'} strong={row.expiringCount > 0}>만료·임박 {row.expiringCount}</Typography.Text>
        <Typography.Text type={row.missingDocCount > 0 ? 'danger' : 'secondary'} strong={row.missingDocCount > 0}>서류 누락 {row.missingDocCount}</Typography.Text>
      </Flex>
    </>
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
        <SearchField label="보유 인증">
          <Select
            allowClear
            style={{ width: 200 }}
            placeholder="전체"
            options={certTypeOptions}
            value={draft.certType}
            onChange={(certType) => setDraft({ ...draft, certType })}
          />
        </SearchField>
        <Checkbox checked={!!draft.missingDocs} onChange={(e) => setDraft({ ...draft, missingDocs: e.target.checked || undefined })}>
          필수 서류 누락만
        </Checkbox>
        <Checkbox checked={!!draft.certExpired} onChange={(e) => setDraft({ ...draft, certExpired: e.target.checked || undefined })}>
          필수 인증 만료만
        </Checkbox>
      </SearchPanel>

      <Flex justify="space-between" align="center" wrap gap={8} style={{ marginBottom: 8 }}>
        <Typography.Text>
          총 <b className="num">{data?.totalElements ?? 0}</b>건
          <Typography.Text type="secondary" className="pc-only" style={{ marginInlineStart: 12, fontSize: 12 }}>
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
        renderCard={renderCard}
        onCardClick={(row) => navigate(`/suppliers/${row.id}`)}
      />

      <SupplierFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </>
  )
}
