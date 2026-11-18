import { useState } from 'react'
import { Alert, App, Breadcrumb, Button, Descriptions, Dropdown, Empty, Flex, Grid, Input, Pagination, Tabs, Typography, type MenuProps } from 'antd'
import { ArrowUpOutlined, FileTextOutlined, FolderAddOutlined, FolderFilled, MoreOutlined, PlusOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { deleteForm, deleteFormFolder, fetchFormCategoryCounts, fetchFormFolder, fetchForms, formCategories, type FormCategory, type FormDetail, type FormFolder, type FormPostItem } from '../../api/forms'
import { toProblem } from '../../api/client'
import { useAuth } from '../../app/AuthContext'
import { useTabTitle } from '../../app/TabsContext'
import { DataList } from '../../components/DataList'
import { formatDateTime } from '../../components/format'
import { FolderEditorModal } from './FolderEditorModal'
import { FormEditorModal } from './FormEditorModal'
import { FormDetailModal } from './FormDetailModal'

/** 최상위 폴더는 자료실 주소를, 하위 폴더는 고유 주소를 사용한다. */
function folderPath(id: number): string { return id === 1 ? '/forms' : `/forms/folders/${id}` }

/** 주소의 폴더가 바뀌면 검색·페이지·열린 모달을 새 폴더 기준으로 초기화한다. */
export function FormsPage() {
  const { folderId: param } = useParams()
  const folderId = param === undefined ? 1 : Number(param)
  if (!Number.isSafeInteger(folderId) || folderId < 1) return <Alert type="error" title="올바른 폴더 주소가 아닙니다." action={<Link to="/forms">자료실로 이동</Link>} />
  return <FolderContents key={folderId} folderId={folderId} />
}

type LibraryRow = { key: string; kind: 'parent'; parentId: number } | { key: string; kind: 'folder'; folder: FormFolder } | { key: string; kind: 'form'; post: FormPostItem }

/** 공유 폴더와 양식을 함께 탐색한다. 검색·분류는 현재 폴더 안의 양식에 적용한다. */
function FolderContents({ folderId }: { folderId: number }) {
  const navigate = useNavigate()
  const { canEdit } = useAuth()
  const mobile = Grid.useBreakpoint().md === false
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [draft, setDraft] = useState('')
  const [category, setCategory] = useState<FormCategory>()
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<number>()
  const [editor, setEditor] = useState<{ post?: FormDetail }>()
  const [folderEditor, setFolderEditor] = useState<{ folder?: FormFolder }>()
  const folder = useQuery({ queryKey: ['form-folders', folderId], queryFn: () => fetchFormFolder(folderId) })
  useTabTitle(folder.data?.folder.name)
  const forms = useQuery({ queryKey: ['forms', folderId, keyword, category, page],
    queryFn: () => fetchForms(folderId, keyword, category, page), enabled: folder.isSuccess })
  // 키가 'forms'로 시작해 refresh()의 무효화에 함께 걸린다.
  const counts = useQuery({ queryKey: ['forms', 'category-counts', folderId, keyword],
    queryFn: () => fetchFormCategoryCounts(folderId, keyword), enabled: folder.isSuccess })
  /** 개수를 아직 못 받았으면 이름만 보여 준다. */
  const tabLabel = (label: string, count?: number) => count === undefined ? label : `${label} (${count})`
  const totalCount = counts.data && Object.values(counts.data).reduce((sum, count) => sum + count, 0)
  // 검색·종류 선택은 양식에만 적용되므로, 그동안은 조건과 무관한 폴더를 숨긴다.
  // 하위 폴더에서는 파일 탐색기처럼 목록 첫 줄에 상위 폴더로 가는 행을 둔다 (#84).
  const parentId = folder.data?.folder.parentId
  const rows: LibraryRow[] = [
    ...(parentId ? [{ key: 'parent', kind: 'parent', parentId } satisfies LibraryRow] : []),
    ...(keyword || category ? [] : folder.data?.children ?? []).map((item): LibraryRow => ({ key: `folder-${item.id}`, kind: 'folder', folder: item })),
    ...(forms.data?.content ?? []).map((post): LibraryRow => ({ key: `form-${post.id}`, kind: 'form', post })),
  ]

  /** 보여줄 폴더·양식이 없을 때의 안내 */
  const emptyText = keyword || category ? '검색 결과가 없습니다.' : '등록된 양식이 없습니다.'

  /** 변경 후 양식과 폴더 목록을 새로 조회한다. */
  const refresh = async () => {
    setPage(0)
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['forms'] }), queryClient.invalidateQueries({ queryKey: ['form-folders'] })])
  }

  /** 저장한 상세를 바로 열고 목록을 갱신한다. */
  const saved = (post: FormDetail) => {
    queryClient.setQueryData(['form', post.id], post)
    setEditor(undefined)
    setSelectedId(post.id)
    void refresh()
  }

  /** 양식과 첨부의 삭제 범위를 확인한 뒤 삭제한다. 실패하면 확인 창을 유지한다. */
  const confirmDeleteForm = (post: Pick<FormDetail, 'id' | 'title'>) => modal.confirm({
    title: '양식을 삭제하시겠습니까?', content: `${post.title} 및 첨부파일을 자료실에서 내려받을 수 없게 됩니다.`,
    okText: '삭제', cancelText: '취소', okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteForm(post.id)
        setSelectedId(undefined)
        queryClient.removeQueries({ queryKey: ['form', post.id] })
        message.success('양식을 삭제했습니다.')
        await refresh()
      } catch (cause) { message.error(toProblem(cause).detail); throw cause }
    },
  })

  /** 서버에서 빈 폴더 여부를 확인하므로 다른 사람이 자료를 추가해도 함께 삭제되지 않는다. */
  const confirmDeleteFolder = (item: FormFolder) => modal.confirm({
    title: '폴더를 삭제하시겠습니까?', content: `${item.name} — 하위 폴더나 양식이 있으면 삭제할 수 없습니다.`,
    okText: '삭제', cancelText: '취소', okButtonProps: { danger: true },
    onOk: async () => {
      try { await deleteFormFolder(item.id); message.success('폴더를 삭제했습니다.'); await refresh() }
      catch (cause) { message.error(toProblem(cause).detail); throw cause }
    },
  })

  /** 폴더·양식의 정보(종류, 위치, 수정·등록 내역)를 창으로 보여준다. */
  const showInfo = (row: Exclude<LibraryRow, { kind: 'parent' }>) => {
    const location = folder.data?.path.map((item) => item.name).join(' / ') ?? '-'
    const item = row.kind === 'folder' ? row.folder : row.post
    modal.info({
      title: row.kind === 'folder' ? row.folder.name : row.post.title, okText: '닫기', icon: null,
      content: (
        <Descriptions column={1} size="small" styles={{ label: { whiteSpace: 'nowrap' } }}>
          <Descriptions.Item label="종류">
            {row.kind === 'folder' ? '폴더' : formCategories.find((category) => category.value === row.post.category)?.label}
          </Descriptions.Item>
          <Descriptions.Item label="위치">{location}</Descriptions.Item>
          {row.kind === 'form' && <Descriptions.Item label="설명"><span style={{ whiteSpace: 'pre-wrap' }}>{row.post.description || '-'}</span></Descriptions.Item>}
          {row.kind === 'form' && <Descriptions.Item label="등록">{formatDateTime(row.post.createdAt)} · {row.post.createdBy ?? '-'}</Descriptions.Item>}
          <Descriptions.Item label="최종 수정">{formatDateTime(item.updatedAt)} · {item.updatedBy ?? '-'}</Descriptions.Item>
        </Descriptions>
      ),
    })
  }

  /**
   * 폴더·양식 한 줄의 메뉴. 마우스 오른쪽 버튼(휴대폰은 ⋯ 단추)으로 연다.
   * 메뉴를 누른 것이 줄 클릭(열기)으로 번지지 않게 막는다.
   */
  const rowMenu = (row: Exclude<LibraryRow, { kind: 'parent' }>, open: () => void): MenuProps => ({
    onClick: ({ domEvent }) => domEvent.stopPropagation(),
    items: [
      { key: 'open', label: '열기', onClick: open },
      { key: 'info', label: '정보 보기', onClick: () => showInfo(row) },
      ...(canEdit ? [
        { type: 'divider' as const },
        ...(row.kind === 'folder' ? [{ key: 'rename', label: '이름 변경', onClick: () => setFolderEditor({ folder: row.folder }) }] : []),
        { key: 'delete', label: '삭제', danger: true, onClick: () => (row.kind === 'folder' ? confirmDeleteFolder(row.folder) : confirmDeleteForm(row.post)) },
      ] : []),
    ],
  })

  /** 목록의 빈 곳을 마우스 오른쪽 버튼으로 눌렀을 때의 메뉴: 새 폴더, 양식 등록, 새로고침 */
  const backgroundMenu: MenuProps = {
    items: [
      ...(canEdit ? [
        { key: 'new-folder', label: '새 폴더', icon: <FolderAddOutlined />, onClick: () => setFolderEditor({}) },
        { key: 'new-form', label: '양식 등록', icon: <PlusOutlined />, onClick: () => setEditor({}) },
        { type: 'divider' as const },
      ] : []),
      { key: 'reload', label: '새로고침', icon: <ReloadOutlined />, onClick: () => void refresh() },
    ],
  }

  /**
   * 목록 한 줄: 파일 탐색기처럼 아이콘·이름·부가 정보를 한 줄에 두고, 줄 전체를 누르면 폴더로 들어가거나 양식을 연다.
   * 마우스 오른쪽 버튼이나 오른쪽 ⋯ 단추로 열기·정보 보기·이름 변경·삭제 메뉴를 연다.
   * 부가 정보는 휴대폰에서는 이름 아래, PC에서는 이름 오른쪽에 놓인다 (data-list-main).
   */
  const renderRow = (row: LibraryRow) => {
    const open = () => row.kind === 'parent' ? navigate(folderPath(row.parentId)) : row.kind === 'folder' ? navigate(folderPath(row.folder.id)) : setSelectedId(row.post.id)
    const name = row.kind === 'parent' ? '상위 폴더' : row.kind === 'folder' ? row.folder.name : row.post.title
    const meta = row.kind === 'parent' ? undefined : row.kind === 'folder'
      ? `폴더 · ${formatDateTime(row.folder.updatedAt)} · ${row.folder.updatedBy ?? '-'}`
      : `${formCategories.find((item) => item.value === row.post.category)?.label} · ${formatDateTime(row.post.updatedAt)} · ${row.post.updatedBy ?? '-'}`
    const menu = row.kind === 'parent' ? undefined : rowMenu(row, open)
    const line = (
      // 줄의 오른쪽 버튼 메뉴가 열릴 때 목록 빈 곳의 메뉴까지 함께 열리지 않게 막는다.
      <div className="data-list-item" role="button" tabIndex={0} onClick={open} onContextMenu={(event) => event.stopPropagation()}
        aria-label={row.kind === 'parent' ? '상위 폴더로 이동' : row.kind === 'folder' ? `폴더 열기: ${name}` : `양식 열기: ${name}`}
        onKeyDown={(event) => { if (event.key === 'Enter' && event.target === event.currentTarget) open() }}>
        {row.kind === 'parent' ? <ArrowUpOutlined style={{ fontSize: 18, width: 26, color: '#6b7280' }} />
          : row.kind === 'folder' ? <FolderFilled style={{ fontSize: 26, color: '#e0a526' }} />
          : <FileTextOutlined style={{ fontSize: 22, width: 26, color: '#6b7280' }} />}
        <div className="data-list-main">
          <div style={{ fontWeight: row.kind === 'form' ? 400 : 600, overflowWrap: 'anywhere' }}>{name}</div>
          {meta && <Typography.Text type="secondary" className="num" style={{ fontSize: 12 }}>{meta}</Typography.Text>}
        </div>
        {menu && (
          <Dropdown trigger={['click']} menu={menu}>
            <Button type="text" icon={<MoreOutlined />} aria-label={`메뉴: ${name}`} onClick={(event) => event.stopPropagation()} />
          </Dropdown>
        )}
        <RightOutlined style={{ fontSize: 12, color: '#bfbfbf', visibility: row.kind === 'folder' ? 'visible' : 'hidden' }} />
      </div>
    )
    return menu ? <Dropdown trigger={['contextMenu']} menu={menu}>{line}</Dropdown> : line
  }

  /** 현재 폴더의 양식 이름 검색창. PC에서는 분류 탭 줄 오른쪽, 휴대폰에서는 탭 줄 위에 놓는다. */
  const searchBox = (
    <Input.Search aria-label="양식 이름 검색" placeholder="현재 폴더의 양식 이름 검색" allowClear value={draft}
      onChange={(event) => setDraft(event.target.value)} onSearch={(value) => { setKeyword(value.trim()); setPage(0) }}
      style={{ width: mobile ? '100%' : 'min(320px, 45vw)' }} />
  )

  return <Flex vertical gap={16}>
    <Flex justify="space-between" align="center" wrap gap={12}>
      <div><Typography.Title level={3} style={{ margin: 0 }}>양식 자료실</Typography.Title>
        <Typography.Text type="secondary">폴더별로 양식을 찾아 내려받으세요.</Typography.Text></div>
      <Flex gap={8} wrap>
        <Button icon={<ReloadOutlined />} loading={folder.isFetching || forms.isFetching} onClick={() => { void folder.refetch(); if (folder.isSuccess) { void forms.refetch(); void counts.refetch() } }}>새로고침</Button>
        {canEdit && folder.isSuccess && <><Button icon={<FolderAddOutlined />} onClick={() => setFolderEditor({})}>새 폴더</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditor({})}>양식 등록</Button></>}
      </Flex>
    </Flex>
    {folder.isError ? <Alert type="error" showIcon title={toProblem(folder.error).detail} action={<Link to="/forms">최상위로 이동</Link>} /> : <>
      {parentId && <Flex gap={12} align="center" wrap>
        <nav aria-label="폴더 경로"><Breadcrumb items={folder.data?.path.map((item, index, all) => ({
          title: index === all.length - 1 ? item.name : <Link to={folderPath(item.id)}>{item.name}</Link>,
        }))} /></nav>
      </Flex>}
      {/* 휴대폰에서는 검색창이 분류 탭을 가리므로 탭 줄 위에 따로 둔다. */}
      {mobile && searchBox}
      <Tabs activeKey={category ?? 'ALL'} onChange={(key) => { setCategory(key === 'ALL' ? undefined : key as FormCategory); setPage(0) }}
        items={[{ key: 'ALL', label: tabLabel('전체', totalCount) },
          ...formCategories.map((item) => ({ key: item.value, label: tabLabel(item.label, counts.data?.[item.value]) }))]} style={{ marginBottom: -16 }}
        tabBarExtraContent={mobile ? undefined : searchBox} />
      {forms.isError && <Alert type="error" showIcon title={toProblem(forms.error).detail} action={<Button onClick={() => void forms.refetch()}>다시 시도</Button>} />}
      {/* 목록 아래 빈 곳까지 마우스 오른쪽 버튼 메뉴가 열리도록 높이를 조금 확보한다. */}
      <Dropdown trigger={['contextMenu']} menu={backgroundMenu}>
        <div style={{ minHeight: 240 }}>
          <DataList rows={rows} rowKey={(row) => row.key} renderRow={renderRow} loading={folder.isLoading || forms.isLoading} emptyText={emptyText} />
          {/* 하위 폴더에서는 "상위 폴더" 줄이 항상 있어 목록이 비지 않으므로, 그 줄뿐이면 빈 안내를 따로 보여준다. */}
          {!folder.isLoading && !forms.isLoading && rows.length > 0 && rows.every((row) => row.kind === 'parent')
            && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />}
        </div>
      </Dropdown>
      {!!forms.data?.totalElements && <Pagination current={page + 1} pageSize={20} total={forms.data.totalElements} showSizeChanger={false}
        onChange={(next) => setPage(next - 1)} align="end" />}
    </>}
    {selectedId !== undefined && !editor && <FormDetailModal id={selectedId} onClose={() => setSelectedId(undefined)} onEdit={(post) => setEditor({ post })} onDelete={confirmDeleteForm} />}
    {canEdit && editor && <FormEditorModal folderId={folderId} post={editor.post} onClose={() => setEditor(undefined)} onSaved={saved} />}
    {canEdit && folderEditor && <FolderEditorModal parentId={folderId} folder={folderEditor.folder} onClose={() => setFolderEditor(undefined)}
      onSaved={() => { setFolderEditor(undefined); void refresh() }} />}
  </Flex>
}
