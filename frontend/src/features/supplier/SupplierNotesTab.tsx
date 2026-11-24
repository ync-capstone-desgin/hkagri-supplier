import { useState } from 'react'
import { Alert, App, Button, Card, DatePicker, Empty, Flex, Form, Input, Modal, Pagination, Spin, Typography } from 'antd'
import { DeleteOutlined, EditOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { toProblem } from '../../api/client'
import { createSupplierNote, deleteSupplierNote, fetchSupplierNotes, updateSupplierNote, type SupplierNote } from '../../api/supplierContacts'
import { useAuth } from '../../app/AuthContext'
import { formatDateTime } from '../../components/format'

/**
 * 업체 상세의 메모 탭 (#49). 업체와 통화·메일로 주고받은 내용을 남겨, 담당자가 바뀌어도 이어받을 수 있게 한다.
 * 소통한 날짜가 최근인 순으로 보여 준다. 작성자 본인과 관리자만 고치거나(#82) 지울 수 있다.
 */
export function SupplierNotesTab({ supplierId }: { supplierId: number }) {
  const { canEdit } = useAuth()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<{ contactDate: Dayjs; content: string }>()
  const [page, setPage] = useState(0)
  const [saving, setSaving] = useState(false)
  const notes = useQuery({ queryKey: ['supplier-notes', supplierId, page], queryFn: () => fetchSupplierNotes(supplierId, page) })

  /** 메모를 남긴 뒤 입력칸을 비우고 첫 쪽으로 돌아간다. */
  const save = async ({ contactDate, content }: { contactDate: Dayjs; content: string }) => {
    setSaving(true)
    try {
      await createSupplierNote(supplierId, contactDate.format('YYYY-MM-DD'), content)
      form.resetFields()
      setPage(0)
      await queryClient.invalidateQueries({ queryKey: ['supplier-notes', supplierId] })
      message.success('메모를 남겼습니다.')
    } catch (e) {
      const problem = toProblem(e)
      if (problem.errors?.length) form.setFields(problem.errors.map((f) => ({ name: f.field as 'contactDate' | 'content', errors: [f.message] })))
      else message.error(problem.detail)
    } finally {
      setSaving(false)
    }
  }

  /** 고치는 중인 메모. 없으면 수정 창을 닫는다. */
  const [editing, setEditing] = useState<SupplierNote>()
  const [editForm] = Form.useForm<{ contactDate: Dayjs; content: string }>()

  /** 고친 날짜와 내용을 저장하고 수정 창을 닫는다. 실패하면 창을 유지한다. */
  const saveEdit = async ({ contactDate, content }: { contactDate: Dayjs; content: string }) => {
    setSaving(true)
    try {
      await updateSupplierNote(supplierId, editing!.id, contactDate.format('YYYY-MM-DD'), content)
      setEditing(undefined)
      await queryClient.invalidateQueries({ queryKey: ['supplier-notes', supplierId] })
      message.success('메모를 수정했습니다.')
    } catch (e) {
      const problem = toProblem(e)
      if (problem.errors?.length) editForm.setFields(problem.errors.map((f) => ({ name: f.field as 'contactDate' | 'content', errors: [f.message] })))
      else message.error(problem.detail)
    } finally {
      setSaving(false)
    }
  }

  /** 확인받은 뒤 메모를 삭제한다. */
  const confirmDelete = (note: SupplierNote) => {
    modal.confirm({
      title: '이 메모를 삭제하시겠습니까?', content: `${note.contactDate} · ${note.authorName}`,
      okText: '삭제', okButtonProps: { danger: true }, cancelText: '취소',
      onOk: async () => {
        try {
          await deleteSupplierNote(supplierId, note.id)
          await queryClient.invalidateQueries({ queryKey: ['supplier-notes', supplierId] })
          message.success('메모를 삭제했습니다.')
        } catch (e) {
          message.error(toProblem(e).detail)
        }
      },
    })
  }

  return (
    <Flex vertical gap={16}>
      {canEdit && (
        <Form form={form} layout="vertical" onFinish={save} disabled={saving} initialValues={{ contactDate: dayjs() }}>
          <Flex gap={12} align="start" wrap>
            <Form.Item name="contactDate" label="소통한 날짜" rules={[{ required: true, message: '소통한 날짜를 선택해 주세요.' }]} style={{ marginBottom: 8 }}>
              <DatePicker allowClear={false} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
            <Form.Item name="content" label="내용" rules={[{ required: true, whitespace: true, message: '내용을 입력해 주세요.' }]}
              style={{ flex: 1, minWidth: 280, marginBottom: 8 }}>
              <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 2, maxRows: 8 }}
                placeholder="예: 김과장과 통화. 12월 납품 단가 인상 요청, 다음 주 견적서 받기로 함" />
            </Form.Item>
          </Flex>
          <Button type="primary" htmlType="submit" loading={saving}>메모 남기기</Button>
        </Form>
      )}
      {notes.isError && <Alert type="error" showIcon title={toProblem(notes.error).detail} />}
      <Spin spinning={notes.isLoading}>
        <Flex vertical gap={8}>
          <Typography.Text type="secondary">메모 {notes.data?.totalElements ?? 0}건 · 소통한 날짜가 최근인 순</Typography.Text>
          {notes.data?.content.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="남긴 메모가 없습니다." />}
          {notes.data?.content.map((note) => (
            <Card key={note.id} size="small">
              <Flex justify="space-between" align="start" gap={12}>
                <div style={{ minWidth: 0 }}>
                  <Typography.Text strong className="num">{note.contactDate}</Typography.Text>
                  <Typography.Text type="secondary" style={{ marginInlineStart: 8 }}>
                    {note.authorName} ({note.author}) · {formatDateTime(note.createdAt)} 작성
                  </Typography.Text>
                  <Typography.Paragraph style={{ margin: '6px 0 0', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{note.content}</Typography.Paragraph>
                </div>
                {note.deletable && canEdit && (
                  <Flex gap={4}>
                    <Button size="small" icon={<EditOutlined />} aria-label={`${note.contactDate} 메모 수정`} onClick={() => setEditing(note)}>수정</Button>
                    <Button size="small" danger icon={<DeleteOutlined />} aria-label={`${note.contactDate} 메모 삭제`} onClick={() => confirmDelete(note)}>삭제</Button>
                  </Flex>
                )}
              </Flex>
            </Card>
          ))}
          {(notes.data?.totalElements ?? 0) > 20 && (
            <Pagination size="small" current={page + 1} pageSize={20} total={notes.data?.totalElements} showSizeChanger={false}
              onChange={(next) => setPage(next - 1)} style={{ alignSelf: 'end' }} />
          )}
        </Flex>
      </Spin>
      <Modal open={!!editing} title="메모 수정" okText="저장" cancelText="취소" destroyOnHidden confirmLoading={saving}
        onOk={() => editForm.submit()} onCancel={() => setEditing(undefined)}>
        {editing && (
          <Form form={editForm} layout="vertical" onFinish={saveEdit} disabled={saving} preserve={false}
            initialValues={{ contactDate: dayjs(editing.contactDate), content: editing.content }}>
            <Form.Item name="contactDate" label="소통한 날짜" rules={[{ required: true, message: '소통한 날짜를 선택해 주세요.' }]}>
              <DatePicker allowClear={false} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
            <Form.Item name="content" label="내용" rules={[{ required: true, whitespace: true, message: '내용을 입력해 주세요.' }]}>
              <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 3, maxRows: 10 }} />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </Flex>
  )
}
