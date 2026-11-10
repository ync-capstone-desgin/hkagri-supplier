import { useRef, useState } from 'react'
import { Alert, App, Form, Input, Modal } from 'antd'
import { createFormFolder, renameFormFolder, type FormFolder } from '../../api/forms'
import { toProblem } from '../../api/client'

/** 현재 위치에서 폴더를 만들거나 선택한 폴더 이름만 바꾼다. */
export function FolderEditorModal({ parentId, folder, onClose, onSaved }: {
  parentId: number
  folder?: FormFolder
  onClose: () => void
  onSaved: () => void
}) {
  const [form] = Form.useForm<{ name: string }>()
  const { message } = App.useApp()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()
  const busy = useRef(false)

  /** 서버의 중복 이름 검증을 거쳐 저장하고, 실패하면 이름 입력을 그대로 둔다. */
  const submit = async ({ name }: { name: string }) => {
    if (busy.current) return
    busy.current = true
    setSaving(true)
    setError(undefined)
    try {
      if (folder) await renameFormFolder(folder.id, name.trim())
      else await createFormFolder(parentId, name.trim())
      message.success(folder ? '폴더 이름을 변경했습니다.' : '폴더를 만들었습니다.')
      onSaved()
    } catch (cause) { setError(toProblem(cause).detail) }
    finally { busy.current = false; setSaving(false) }
  }

  return <Modal open title={folder ? '폴더 이름 변경' : '새 폴더'} okText="저장" cancelText="취소"
    onOk={() => form.submit()} onCancel={() => { if (!saving) onClose() }} confirmLoading={saving}
    cancelButtonProps={{ disabled: saving }} closable={!saving} keyboard={!saving} mask={{ closable: !saving }}>
    <Form form={form} layout="vertical" disabled={saving} onFinish={submit} initialValues={{ name: folder?.name ?? '' }} style={{ marginTop: 16 }}>
      <Form.Item name="name" label="폴더 이름" rules={[{ required: true, whitespace: true, message: '폴더 이름을 입력해 주세요.' }, { max: 100 }]}>
        <Input maxLength={100} autoFocus onPressEnter={() => form.submit()} />
      </Form.Item>
      {error && <Alert type="error" showIcon title={error} />}
    </Form>
  </Modal>
}
