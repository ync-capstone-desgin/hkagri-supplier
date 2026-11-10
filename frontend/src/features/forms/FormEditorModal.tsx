import { useRef, useState } from 'react'
import { Alert, App, Button, Flex, Form, Input, Modal, Select, Typography, Upload, type UploadFile } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { formCategories, saveForm, type FormDetail, type FormInput } from '../../api/forms'
import { toProblem } from '../../api/client'
import { useSaveShortcut } from '../../hooks/useSaveShortcut'
import { formFileAccept, formFileSize, formUploadError } from './formFiles'

/** 양식 등록·수정. 기존 첨부 삭제도 저장할 때만 적용하며 실패하면 입력과 파일 선택을 유지한다. */
export function FormEditorModal({ folderId, post, onClose, onSaved }: {
  folderId: number
  post?: FormDetail
  onClose: () => void
  onSaved: (saved: FormDetail) => void
}) {
  const [form] = Form.useForm<FormInput>()
  const { message } = App.useApp()
  const [uploads, setUploads] = useState<UploadFile[]>([])
  const [removeIds, setRemoveIds] = useState<number[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()
  const busy = useRef(false)
  const files = uploads.flatMap((file) => file.originFileObj ? [file.originFileObj] : [])
  const uploadError = formUploadError(files)
  useSaveShortcut(!saving, () => form.submit())

  /** 삭제 예약을 포함한 변경을 한 번에 저장한다. */
  const submit = async (values: FormInput) => {
    if (busy.current || uploadError) return
    busy.current = true
    setSaving(true)
    setError(undefined)
    try {
      const saved = await saveForm(post?.id, { ...values, title: values.title.trim(), folderId, removeFileIds: removeIds }, files)
      message.success(post ? '양식을 수정했습니다.' : '양식을 등록했습니다.')
      onSaved(saved)
    } catch (cause) {
      const problem = toProblem(cause)
      setError(problem.detail)
      for (const field of problem.errors ?? []) {
        if (field.field === 'title' || field.field === 'description' || field.field === 'category') {
          form.setFields([{ name: field.field, errors: [field.message] }])
        }
      }
    } finally {
      busy.current = false
      setSaving(false)
    }
  }

  /** 기존 첨부의 삭제 예약을 추가하거나 취소한다. 서버 파일은 저장 전까지 유지한다. */
  const toggleRemoval = (id: number) => setRemoveIds((ids) => ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id])

  return (
    <Modal open title={post ? '양식 수정' : '양식 등록'} width={680} okText="저장" cancelText="취소"
      onOk={() => form.submit()} onCancel={() => { if (!saving) onClose() }} confirmLoading={saving}
      okButtonProps={{ disabled: !!uploadError }} cancelButtonProps={{ disabled: saving }} closable={!saving} keyboard={!saving} mask={{ closable: !saving }}>
      <Form form={form} layout="vertical" disabled={saving} onFinish={submit} style={{ marginTop: 16 }}
        initialValues={{ title: post?.title ?? '', description: post?.description ?? '', category: post?.category ?? 'OTHER' }}>
        <Form.Item name="title" label="양식 이름" rules={[{ required: true, whitespace: true, message: '양식 이름을 입력해 주세요.' }, { max: 200 }]}>
          <Input maxLength={200} autoFocus placeholder="예: 농산물 거래 계약서 양식" />
        </Form.Item>
        <Form.Item name="category" label="분류" rules={[{ required: true, message: '분류를 선택해 주세요.' }]}>
          <Select options={formCategories} />
        </Form.Item>
        <Form.Item name="description" label="설명" rules={[{ max: 5000 }]}>
          <Input.TextArea rows={3} maxLength={5000} showCount placeholder="사용 방법과 개정일 등을 적어 주세요." />
        </Form.Item>
        {!!post?.files.length && <Flex vertical gap={8} style={{ marginBottom: 20 }}>
          <Typography.Text strong>기존 첨부파일</Typography.Text>
          {post.files.map((file) => {
            const removed = removeIds.includes(file.id)
            return <Flex key={file.id} gap={8} align="center" justify="space-between">
              <Typography.Text delete={removed} style={{ overflowWrap: 'anywhere' }}>{file.originalName} ({formFileSize(file.size)}){removed && ' — 삭제 예정'}</Typography.Text>
              <Button size="small" danger={!removed} disabled={saving} aria-label={`${removed ? '첨부 삭제 취소' : '첨부 삭제'}: ${file.originalName}`}
                onClick={() => toggleRemoval(file.id)}>{removed ? '삭제 취소' : '삭제'}</Button>
            </Flex>
          })}
          <Typography.Text type="secondary">첨부파일 삭제는 저장을 눌러야 적용됩니다.</Typography.Text>
        </Flex>}
        <Form.Item label="새 첨부파일 (선택)">
          <Upload.Dragger multiple accept={formFileAccept} fileList={uploads} disabled={saving} beforeUpload={() => false}
            onChange={({ fileList }) => setUploads(fileList)}>
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p className="ant-upload-text">파일을 끌어다 놓거나 클릭해서 선택하세요</p>
            <p className="ant-upload-hint">PDF·이미지·한글·오피스 문서. 새 파일 합계 20MB까지 선택할 수 있습니다.</p>
          </Upload.Dragger>
        </Form.Item>
        {uploadError && <Alert type="error" showIcon title={uploadError} />}
        {error && <Alert type="error" showIcon title={error} style={{ marginTop: 12 }} />}
      </Form>
    </Modal>
  )
}
