import { App, Button, Flex, List, Typography, Upload } from 'antd'
import { DeleteOutlined, DownloadOutlined, InboxOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { deleteStoredFile, downloadStoredFile, uploadSupplierFile, type FileInfo } from '../api/files'
import { toProblem } from '../api/client'

/** 파일 크기를 화면에서 읽기 쉬운 단위로 표시한다. */
function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * 계약·인증 수정 창의 첨부파일 섹션 (FR-021, FR-012).
 * 이미 저장된 건(id가 있는 수정 모드)에서만 쓴다. 등록 중인 새 건은 id가 없어 연결할 수 없으므로,
 * 먼저 저장한 뒤 다시 열어 첨부하도록 안내한다.
 */
export function AttachmentField({ queryKey, supplierId, target, fetchFiles, onChanged }: {
  /** 목록 캐시 구분용 키 (예: ['contract-files', 3]) */
  queryKey: unknown[]
  supplierId: number
  /** 'contract:3' 또는 'certification:5' 형식 */
  target: string
  fetchFiles: () => Promise<FileInfo[]>
  /** 올리기·삭제 후 호출. 목록 화면의 첨부 개수 캐시도 함께 새로 고치는 데 쓴다. */
  onChanged?: () => void
}) {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const files = useQuery({ queryKey, queryFn: fetchFiles })

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey })
    onChanged?.()
  }

  /** 파일명을 확인받은 뒤 삭제한다. */
  const confirmDelete = (f: FileInfo) => {
    modal.confirm({
      title: '파일을 삭제하시겠습니까?', content: f.originalName, okText: '삭제', cancelText: '취소', okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteStoredFile(f.id)
          await refresh()
        } catch (error) {
          message.error(toProblem(error).detail)
          throw error
        }
      },
    })
  }

  /** 내려받기 오류는 화면에 안내한다. */
  const download = async (id: number) => {
    try { await downloadStoredFile(id) } catch (error) { message.error(toProblem(error).detail) }
  }

  return (
    <Flex vertical gap={8}>
      {!!files.data?.length && (
        <List size="small" dataSource={files.data} loading={files.isLoading}
          renderItem={(f) => (
            <List.Item actions={[
              <Button key="dl" type="link" size="small" icon={<DownloadOutlined />} onClick={() => void download(f.id)} />,
              <Button key="del" type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDelete(f)} />,
            ]}>
              <span style={{ overflowWrap: 'anywhere' }}>{f.originalName}</span>
              <Typography.Text type="secondary" style={{ marginInlineStart: 8 }}>{fileSize(f.size)}</Typography.Text>
            </List.Item>
          )} />
      )}
      <Upload.Dragger accept=".pdf,.jpg,.jpeg,.png,.gif,.hwp,.hwpx,.doc,.docx,.xls,.xlsx,.ppt,.pptx"
        customRequest={async ({ file, onSuccess, onError }) => {
          try {
            const saved = await uploadSupplierFile(supplierId, file as File, target)
            onSuccess?.(saved)
            await refresh()
          } catch (error) {
            const detail = toProblem(error).detail
            onError?.(new Error(detail))
            message.error(`${(file as File).name}: ${detail}`)
          }
        }} showUploadList={false} style={{ padding: '4px 0' }}>
        <p className="ant-upload-drag-icon" style={{ margin: 4, fontSize: 20 }}><InboxOutlined /></p>
        <p className="ant-upload-text" style={{ fontSize: 13, margin: 0 }}>파일을 끌어다 놓거나 클릭해서 올리세요</p>
      </Upload.Dragger>
    </Flex>
  )
}
