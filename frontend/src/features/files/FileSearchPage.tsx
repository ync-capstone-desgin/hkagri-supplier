import { useState } from 'react'
import { App, Button, Card, Descriptions, Dropdown, Flex, Input, Pagination, Select, Tag, Typography, type MenuProps } from 'antd'
import { DownloadOutlined, FileOutlined, MoreOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { toProblem } from '../../api/client'
import { useAuth } from '../../app/AuthContext'
import { useFileCategories } from '../../hooks/useFileCategories'
import { FileCategoryModal } from './FileCategoryModal'
import { downloadStoredFile, searchFiles, type FileSearchItem } from '../../api/files'
import { DataList } from '../../components/DataList'
import { fileSize, formatDateTime } from '../../components/format'
import { SearchField, SearchPanel } from '../../components/SearchPanel'

/** 파일이 있는 곳별 태그 색 */
const LOCATION_COLOR: Record<string, string> = {
  '업체 자료': 'green', '계약 첨부': 'blue', '인증 첨부': 'cyan', '양식 자료실': 'gold',
}

/** 검색 조건: 검색어와 자료 종류 */
interface Search {
  keyword: string
  categoryId?: number
}

const EMPTY: Search = { keyword: '' }

/**
 * 자료 검색 화면 (#53, #54).
 * 관리자에게는 자료 종류를 추가·수정하는 [자료 종류 관리] 버튼이 보인다.
 * 어느 업체에 올렸는지 몰라도 파일 이름·업체명·계약명·인증 종류·양식 제목으로 모든 자료를 한 번에 찾는다.
 * 결과에서 바로 내려받거나, 그 파일이 있는 업체 상세·양식 폴더로 이동한다.
 * 결과는 파일 탐색기 모양 목록이며, 줄이나 빈 곳을 마우스 오른쪽 버튼으로 누르면 메뉴가 열린다.
 */
export function FileSearchPage() {
  const navigate = useNavigate()
  const { message, modal } = App.useApp()
  const { me } = useAuth()
  const categories = useFileCategories()
  const [draft, setDraft] = useState<Search>(EMPTY)
  const [search, setSearch] = useState<Search>(EMPTY)
  const [page, setPage] = useState(0)
  const [categoriesOpen, setCategoriesOpen] = useState(false)
  const files = useQuery({ queryKey: ['file-search', search, page], queryFn: () => searchFiles(search.keyword, search.categoryId, page) })

  /** 검색 조건을 적용하고 첫 쪽부터 다시 조회한다. */
  const apply = (next: Search) => {
    setDraft(next)
    setSearch(next)
    setPage(0)
  }

  /** 파일을 내려받는다. 그사이 삭제됐으면 서버 안내를 보여 주고 목록을 새로 고친다. */
  const download = async (id: number) => {
    try {
      await downloadStoredFile(id)
    } catch (e) {
      message.error(toProblem(e).detail)
      void files.refetch()
    }
  }

  /** 그 파일이 있는 업체 상세나 양식 폴더로 이동한다. */
  const openLocation = (row: FileSearchItem) =>
    navigate(row.supplierId ? `/suppliers/${row.supplierId}?tab=files` : row.folderId === 1 ? '/forms' : `/forms/folders/${row.folderId}`)

  /** 파일의 정보(위치, 연결된 계약·인증, 종류, 크기, 올린 내역)를 창으로 보여준다. */
  const showInfo = (row: FileSearchItem) => modal.info({
    title: <span style={{ overflowWrap: 'anywhere' }}>{row.file.originalName}</span>, okText: '닫기', icon: null,
    content: (
      <Descriptions column={1} size="small" styles={{ label: { whiteSpace: 'nowrap' } }}>
        <Descriptions.Item label="위치">{row.location}</Descriptions.Item>
        <Descriptions.Item label={row.supplierId ? '업체' : '양식'}>{row.supplierName ?? row.formTitle ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="연결된 계약·인증">{row.contractName ?? row.certificationName ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="자료 종류">{row.categoryName ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="크기">{fileSize(row.file.size)}</Descriptions.Item>
        <Descriptions.Item label="올린 내역">{formatDateTime(row.file.createdAt)} · {row.file.createdBy ?? '-'}</Descriptions.Item>
      </Descriptions>
    ),
  })

  /** 파일 한 줄의 메뉴. 마우스 오른쪽 버튼(휴대폰은 ⋯ 단추)으로 연다. */
  const rowMenu = (row: FileSearchItem): MenuProps => ({
    items: [
      { key: 'download', label: '내려받기', icon: <DownloadOutlined />, onClick: () => void download(row.file.id) },
      { key: 'info', label: '정보 보기', onClick: () => showInfo(row) },
      { key: 'location', label: row.supplierId ? '업체 자료로 이동' : '양식 폴더로 이동', onClick: () => openLocation(row) },
    ],
  })

  /** 목록의 빈 곳을 마우스 오른쪽 버튼으로 눌렀을 때의 메뉴: 새로고침, (관리자) 자료 종류 관리 */
  const backgroundMenu: MenuProps = {
    items: [
      { key: 'reload', label: '새로고침', icon: <ReloadOutlined />, onClick: () => void files.refetch() },
      ...(me?.role === 'ADMIN' ? [{ key: 'categories', label: '자료 종류 관리', icon: <SettingOutlined />, onClick: () => setCategoriesOpen(true) }] : []),
    ],
  }

  /**
   * 목록 한 줄: 파일 이름과 부가 정보(위치·업체나 양식·연결된 계약·종류·올린 날짜), 오른쪽에 내려받기와 ⋯ 메뉴.
   * 부가 정보는 휴대폰에서는 이름 아래, PC에서는 이름 오른쪽에 놓인다 (data-list-main).
   */
  const renderRow = (row: FileSearchItem) => (
    <Dropdown trigger={['contextMenu']} menu={rowMenu(row)}>
      {/* 줄의 메뉴가 열릴 때 목록 빈 곳의 메뉴까지 함께 열리지 않게 막는다. */}
      <div className="data-list-item" onContextMenu={(event) => event.stopPropagation()}>
        <FileOutlined style={{ fontSize: 22, width: 26, color: '#6b7280' }} />
        <div className="data-list-main">
          <div style={{ overflowWrap: 'anywhere' }}>{row.file.originalName}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            <Tag color={LOCATION_COLOR[row.location]}>{row.location}</Tag>
            <Typography.Link onClick={() => openLocation(row)}>{row.supplierName ?? row.formTitle}</Typography.Link>
            {(row.contractName ?? row.certificationName) && ` · ${row.contractName ?? row.certificationName}`}
            {row.categoryName && ` · ${row.categoryName}`}
            {' · '}<span className="num" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(row.file.createdAt)}</span>
          </Typography.Text>
        </div>
        <Button icon={<DownloadOutlined />} aria-label={`내려받기: ${row.file.originalName}`} onClick={() => void download(row.file.id)} />
        <Dropdown trigger={['click']} menu={rowMenu(row)}>
          <Button type="text" icon={<MoreOutlined />} aria-label={`메뉴: ${row.file.originalName}`} />
        </Dropdown>
      </div>
    </Dropdown>
  )

  return (
    <>
      <SearchPanel onSearch={() => apply(draft)} onReset={() => apply(EMPTY)}>
        <SearchField label="검색어">
          <Input allowClear autoFocus placeholder="파일 이름·업체명·계약명·인증 종류·양식 제목" style={{ width: 'min(360px, 60vw)' }}
            value={draft.keyword} onChange={(e) => setDraft({ ...draft, keyword: e.target.value })} />
        </SearchField>
        <SearchField label="자료 종류">
          <Select allowClear placeholder="전체" style={{ width: 170 }} options={categories.options}
            value={draft.categoryId} onChange={(categoryId) => setDraft({ ...draft, categoryId })} />
        </SearchField>
      </SearchPanel>
      <Card>
        <Flex justify="space-between" align="center" wrap gap={8} style={{ marginBottom: 8 }}>
          <Typography.Text type="secondary">자료 {files.data?.totalElements ?? 0}개 · 최근에 올린 순</Typography.Text>
          {me?.role === 'ADMIN' && <Button icon={<SettingOutlined />} onClick={() => setCategoriesOpen(true)}>자료 종류 관리</Button>}
        </Flex>
        {/* 목록 아래 빈 곳까지 마우스 오른쪽 버튼 메뉴가 열리도록 높이를 조금 확보한다. */}
        <Dropdown trigger={['contextMenu']} menu={backgroundMenu}>
          <div style={{ minHeight: 240 }}>
            <DataList rows={files.data?.content} rowKey={(row) => String(row.file.id)} renderRow={renderRow} loading={files.isLoading}
              emptyText={search.keyword || search.categoryId ? '조건에 맞는 자료가 없습니다.' : '등록된 자료가 없습니다.'} />
          </div>
        </Dropdown>
        {!!files.data?.totalElements && <Pagination current={page + 1} pageSize={20} total={files.data.totalElements} showSizeChanger={false}
          onChange={(next) => setPage(next - 1)} align="end" style={{ marginTop: 12 }} />}
      </Card>
      <FileCategoryModal open={categoriesOpen} onClose={() => setCategoriesOpen(false)} />
    </>
  )
}
