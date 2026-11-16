import { useState } from 'react'
import { Alert, App, Button, Flex, Modal, Skeleton, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { fetchForm, formCategories, type FormDetail } from '../../api/forms'
import { downloadStoredFile } from '../../api/files'
import { toProblem } from '../../api/client'
import { useAuth } from '../../app/AuthContext'
import { formatDateTime } from '../../components/format'
import { formFileSize } from './formFiles'

/** 양식 설명·감사 정보와 개별 첨부 다운로드. 조회자는 편집 버튼이 없다. */
export function FormDetailModal({ id, onClose, onEdit, onDelete }: {
  id: number
  onClose: () => void
  onEdit: (post: FormDetail) => void
  onDelete: (post: FormDetail) => void
}) {
  const { canEdit } = useAuth()
  const { message } = App.useApp()
  const [downloading, setDownloading] = useState<number>()
  const query = useQuery({ queryKey: ['form', id], queryFn: () => fetchForm(id) })
  const post = query.data

  /** 파일 오류를 안내하고 다운로드 버튼의 대기 상태를 해제한다. */
  const download = async (fileId: number) => {
    setDownloading(fileId)
    try { await downloadStoredFile(fileId) } catch (cause) { message.error(toProblem(cause).detail) }
    finally { setDownloading(undefined) }
  }

  return <Modal open title="양식 상세" width={700} onCancel={onClose} footer={<Flex justify="flex-end" gap={8}>
    {canEdit && post && <><Button danger onClick={() => onDelete(post)}>삭제</Button><Button onClick={() => onEdit(post)}>수정</Button></>}
    <Button onClick={onClose}>닫기</Button>
  </Flex>}>
    {query.isLoading && <Skeleton active />}
    {query.isError && <Alert type="error" showIcon title={toProblem(query.error).detail}
      action={<Button onClick={() => void query.refetch()}>다시 시도</Button>} />}
    {post && <Flex vertical gap={16} style={{ marginTop: 16 }}>
      <div><Typography.Title level={4} style={{ margin: '0 0 8px', overflowWrap: 'anywhere' }}>{post.title}</Typography.Title>
        <Tag>{formCategories.find((item) => item.value === post.category)?.label}</Tag>
      </div>
      <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: 0 }}>{post.description || '등록된 설명이 없습니다.'}</Typography.Paragraph>
      <Typography.Text type="secondary">등록 {formatDateTime(post.createdAt)} · {post.createdBy ?? '-'} / 수정 {formatDateTime(post.updatedAt)} · {post.updatedBy ?? '-'}</Typography.Text>
      <Typography.Text strong>첨부파일 {post.files.length}개</Typography.Text>
      {post.files.length === 0 && <Typography.Text type="secondary">첨부파일이 없습니다.</Typography.Text>}
      {post.files.map((file) => <Flex key={file.id} gap={12} align="center" justify="space-between">
        <div style={{ minWidth: 0 }}><Typography.Text style={{ overflowWrap: 'anywhere' }}>{file.originalName}</Typography.Text><br />
          <Typography.Text type="secondary">{formFileSize(file.size)}</Typography.Text></div>
        <Button icon={<DownloadOutlined />} aria-label={`내려받기: ${file.originalName}`} loading={downloading === file.id}
          disabled={downloading !== undefined && downloading !== file.id} onClick={() => void download(file.id)}>내려받기</Button>
      </Flex>)}
    </Flex>}
  </Modal>
}
