import { useState } from 'react'
import { Alert, App, Breadcrumb, Button, Flex, Input, Pagination, Table, Tabs, Tag, Typography } from 'antd'
import { ArrowUpOutlined, FileTextOutlined, FolderAddOutlined, FolderOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { deleteForm, deleteFormFolder, fetchFormFolder, fetchForms, formCategories, type FormCategory, type FormDetail, type FormFolder, type FormPostItem } from '../../api/forms'
import { toProblem } from '../../api/client'
import { useAuth } from '../../app/AuthContext'
import { useTabTitle } from '../../app/TabsContext'
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

type LibraryRow = { key: string; kind: 'folder'; folder: FormFolder } | { key: string; kind: 'form'; post: FormPostItem }

/** 공유 폴더와 양식을 함께 탐색한다. 검색·분류는 현재 폴더 안의 양식에 적용한다. */
function FolderContents({ folderId }: { folderId: number }) {
  const navigate = useNavigate()
  const { canEdit } = useAuth()
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
  const rows: LibraryRow[] = [
    ...(folder.data?.children ?? []).map((item): LibraryRow => ({ key: `folder-${item.id}`, kind: 'folder', folder: item })),
    ...(forms.data?.content ?? []).map((post): LibraryRow => ({ key: `form-${post.id}`, kind: 'form', post })),
  ]

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
  const confirmDeleteForm = (post: FormDetail) => modal.confirm({
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

  return <Flex vertical gap={16}>
    <Flex justify="space-between" align="center" wrap gap={12}>
      <div><Typography.Title level={3} style={{ margin: 0 }}>양식 자료실</Typography.Title>
        <Typography.Text type="secondary">폴더별로 양식을 찾아 내려받으세요.</Typography.Text></div>
      <Flex gap={8} wrap>
        <Button icon={<ReloadOutlined />} loading={folder.isFetching || forms.isFetching} onClick={() => { void folder.refetch(); if (folder.isSuccess) void forms.refetch() }}>새로고침</Button>
        {canEdit && folder.isSuccess && <><Button icon={<FolderAddOutlined />} onClick={() => setFolderEditor({})}>새 폴더</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditor({})}>양식 등록</Button></>}
      </Flex>
    </Flex>
    {folder.isError ? <Alert type="error" showIcon title={toProblem(folder.error).detail} action={<Link to="/forms">최상위로 이동</Link>} /> : <>
      <Flex gap={12} align="center" wrap>
        {folder.data?.folder.parentId && <Button icon={<ArrowUpOutlined />} onClick={() => navigate(folderPath(folder.data!.folder.parentId!))}>상위 폴더</Button>}
        <nav aria-label="폴더 경로"><Breadcrumb items={folder.data?.path.map((item, index, all) => ({
          title: index === all.length - 1 ? item.name : <Link to={folderPath(item.id)}>{item.name}</Link>,
        }))} /></nav>
      </Flex>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Text>폴더 {folder.data?.children.length ?? 0}개, 양식 {forms.data?.totalElements ?? 0}개</Typography.Text>
        <Input.Search aria-label="양식 이름 검색" placeholder="현재 폴더의 양식 이름 검색" allowClear value={draft}
          onChange={(event) => setDraft(event.target.value)} onSearch={(value) => { setKeyword(value.trim()); setPage(0) }} style={{ width: 320, maxWidth: '100%' }} />
      </Flex>
      <Tabs activeKey={category ?? 'ALL'} onChange={(key) => { setCategory(key === 'ALL' ? undefined : key as FormCategory); setPage(0) }}
        items={[{ key: 'ALL', label: '전체' }, ...formCategories.map((item) => ({ key: item.value, label: item.label }))]} style={{ marginBottom: -16 }} />
      {forms.isError && <Alert type="error" showIcon title={toProblem(forms.error).detail} action={<Button onClick={() => void forms.refetch()}>다시 시도</Button>} />}
      <Table<LibraryRow> rowKey="key" dataSource={rows} loading={folder.isLoading || forms.isLoading} pagination={false} size="middle" scroll={{ x: 750 }}
        locale={{ emptyText: keyword || category ? '검색 결과가 없습니다.' : '등록된 양식이 없습니다.' }}
        columns={[
          { title: '이름', key: 'name', render: (_, row) => row.kind === 'folder'
            ? <Button type="link" icon={<FolderOutlined />} aria-label={`폴더 열기: ${row.folder.name}`} onClick={() => navigate(folderPath(row.folder.id))} style={{ padding: 0, height: 'auto', whiteSpace: 'normal', textAlign: 'left' }}>{row.folder.name}</Button>
            : <Button type="link" icon={<FileTextOutlined />} aria-label={`양식 열기: ${row.post.title}`} onClick={() => setSelectedId(row.post.id)} style={{ padding: 0, height: 'auto', whiteSpace: 'normal', textAlign: 'left', overflowWrap: 'anywhere' }}>{row.post.title}</Button> },
          { title: '종류', width: 120, render: (_, row) => row.kind === 'folder' ? '폴더' : <Tag>{formCategories.find((item) => item.value === row.post.category)?.label}</Tag> },
          { title: '수정일', width: 160, render: (_, row) => formatDateTime(row.kind === 'folder' ? row.folder.updatedAt : row.post.updatedAt) },
          { title: '수정한 사람', width: 110, render: (_, row) => (row.kind === 'folder' ? row.folder.updatedBy : row.post.updatedBy) ?? '-' },
          ...(canEdit ? [{ title: '폴더 관리', width: 160, render: (_: unknown, row: LibraryRow) => row.kind === 'folder' && <Flex gap={4}>
            <Button size="small" aria-label={`폴더 이름 변경: ${row.folder.name}`} onClick={() => setFolderEditor({ folder: row.folder })}>이름 변경</Button>
            <Button size="small" danger aria-label={`폴더 삭제: ${row.folder.name}`} onClick={() => confirmDeleteFolder(row.folder)}>삭제</Button>
          </Flex> }] : []),
        ]} />
      {!!forms.data?.totalElements && <Pagination current={page + 1} pageSize={20} total={forms.data.totalElements} showSizeChanger={false}
        onChange={(next) => setPage(next - 1)} align="end" />}
    </>}
    {selectedId !== undefined && !editor && <FormDetailModal id={selectedId} onClose={() => setSelectedId(undefined)} onEdit={(post) => setEditor({ post })} onDelete={confirmDeleteForm} />}
    {canEdit && editor && <FormEditorModal folderId={folderId} post={editor.post} onClose={() => setEditor(undefined)} onSaved={saved} />}
    {canEdit && folderEditor && <FolderEditorModal parentId={folderId} folder={folderEditor.folder} onClose={() => setFolderEditor(undefined)}
      onSaved={() => { setFolderEditor(undefined); void refresh() }} />}
  </Flex>
}
