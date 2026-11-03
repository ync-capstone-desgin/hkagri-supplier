import { useState } from 'react'
import { App, Badge, Button, List, Popover } from 'antd'
import { DownloadOutlined, PaperClipOutlined } from '@ant-design/icons'
import { downloadStoredFile, type FileInfo } from '../api/files'
import { toProblem } from '../api/client'

/**
 * 계약·인증 목록의 첨부 아이콘 (FR-021, FR-012).
 * 자료가 없으면 아무것도 표시하지 않는다. 1개면 눌렀을 때 바로 내려받고, 여러 개면 목록 팝오버를 보여준다.
 */
export function AttachmentIcon({ count, fetchFiles }: { count: number; fetchFiles: () => Promise<FileInfo[]> }) {
  const { message } = App.useApp()
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [files, setFiles] = useState<FileInfo[]>()

  if (!count) return null

  /** 내려받기 오류는 화면에 안내한다. */
  const download = async (id: number) => {
    try {
      await downloadStoredFile(id)
    } catch (error) {
      message.error(toProblem(error).detail)
    }
  }

  /** 자료가 1개면 바로 내려받고, 여러 개면 목록을 불러와 팝오버로 보여준다. */
  const handleClick = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (count === 1 && !files) {
      setLoading(true)
      try {
        const list = await fetchFiles()
        if (list.length === 1) {
          await download(list[0].id)
          return
        }
        setFiles(list)
        setOpen(true)
      } finally {
        setLoading(false)
      }
      return
    }
    if (!files) {
      setLoading(true)
      try {
        setFiles(await fetchFiles())
      } finally {
        setLoading(false)
      }
    }
    setOpen(true)
  }

  return (
    <Popover open={open} onOpenChange={setOpen} trigger="click"
      content={<List size="small" style={{ maxWidth: 280 }} dataSource={files}
        renderItem={(f) => (
          <List.Item>
            <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => void download(f.id)}
              style={{ overflowWrap: 'anywhere', height: 'auto', whiteSpace: 'normal', textAlign: 'left' }}>
              {f.originalName}
            </Button>
          </List.Item>
        )} />}>
      <Badge size="small" count={count} offset={[0, 6]} color="blue">
        <Button type="text" size="small" icon={<PaperClipOutlined />} loading={loading} onClick={(e) => void handleClick(e)} />
      </Badge>
    </Popover>
  )
}
