import { useState } from 'react'
import { App, Button, Card, Flex, Input, Select, Table, Tag, Typography } from 'antd'
import { DownloadOutlined, SettingOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { toProblem } from '../../api/client'
import { useAuth } from '../../app/AuthContext'
import { useFileCategories } from '../../hooks/useFileCategories'
import { FileCategoryModal } from './FileCategoryModal'
import { downloadStoredFile, searchFiles, type FileSearchItem } from '../../api/files'
import { formatDateTime } from '../../components/format'
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
 */
export function FileSearchPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
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

  return (
    <>
      <SearchPanel onSearch={() => apply(draft)} onReset={() => apply(EMPTY)}>
        <SearchField label="검색어">
          <Input allowClear autoFocus placeholder="파일 이름·업체명·계약명·인증 종류·양식 제목" style={{ width: 360 }}
            value={draft.keyword} onChange={(e) => setDraft({ ...draft, keyword: e.target.value })} />
        </SearchField>
        <SearchField label="자료 종류">
          <Select allowClear placeholder="전체" style={{ width: 170 }} options={categories.options}
            value={draft.categoryId} onChange={(categoryId) => setDraft({ ...draft, categoryId })} />
        </SearchField>
      </SearchPanel>
      <Card>
        <Flex justify="space-between" align="center" style={{ marginBottom: 8 }}>
          <Typography.Text type="secondary">자료 {files.data?.totalElements ?? 0}개 · 최근에 올린 순</Typography.Text>
          {me?.role === 'ADMIN' && <Button icon={<SettingOutlined />} onClick={() => setCategoriesOpen(true)}>자료 종류 관리</Button>}
        </Flex>
        <Table<FileSearchItem> rowKey={(row) => row.file.id} dataSource={files.data?.content} loading={files.isLoading} size="small"
          scroll={{ x: 1130 }} locale={{ emptyText: search.keyword || search.categoryId ? '조건에 맞는 자료가 없습니다.' : '등록된 자료가 없습니다.' }}
          pagination={{ current: page + 1, pageSize: 20, total: files.data?.totalElements ?? 0, showSizeChanger: false, onChange: (next) => setPage(next - 1) }}
          columns={[
            { title: '파일 이름', width: 260, render: (_, row) => <span style={{ overflowWrap: 'anywhere' }}>{row.file.originalName}</span> },
            { title: '자료 종류', width: 130, render: (_, row) => row.categoryName ?? '-' },
            { title: '위치', width: 110, render: (_, row) => <Tag color={LOCATION_COLOR[row.location]}>{row.location}</Tag> },
            {
              title: '업체 · 양식', width: 220,
              render: (_, row) => row.supplierId
                ? <Typography.Link onClick={() => navigate(`/suppliers/${row.supplierId}`)}>{row.supplierName}</Typography.Link>
                : <Typography.Link onClick={() => navigate(row.folderId === 1 ? '/forms' : `/forms/folders/${row.folderId}`)}>{row.formTitle}</Typography.Link>,
            },
            { title: '연결된 계약·인증', render: (_, row) => row.contractName ?? row.certificationName ?? '-' },
            { title: '올린 사람', width: 110, render: (_, row) => row.file.createdBy ?? '-' },
            { title: '올린 날짜', width: 150, render: (_, row) => formatDateTime(row.file.createdAt) },
            { title: '작업', width: 110, render: (_, row) => <Button size="small" icon={<DownloadOutlined />} onClick={() => void download(row.file.id)}>내려받기</Button> },
          ]} />
      </Card>
      <FileCategoryModal open={categoriesOpen} onClose={() => setCategoriesOpen(false)} />
    </>
  )
}
