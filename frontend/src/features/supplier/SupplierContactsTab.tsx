import { useState } from 'react'
import { Alert, App, Button, Flex, Form, Input, Modal, Table, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toProblem } from '../../api/client'
import {
  createSupplierContact, deleteSupplierContact, fetchSupplierContacts, updateSupplierContact,
  type SupplierContact, type SupplierContactRequest,
} from '../../api/supplierContacts'
import { useAuth } from '../../app/AuthContext'

/**
 * 업체 상세의 담당자 탭 (#49).
 * 기본정보의 업체 담당자(대표 담당자) 외에 연락하는 사람을 여러 명 등록한다. 조회자는 보기만 한다.
 */
export function SupplierContactsTab({ supplierId }: { supplierId: number }) {
  const { canEdit } = useAuth()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<SupplierContactRequest>()
  /** 열려 있는 입력 창: 'new'면 추가, 담당자면 그 담당자 수정, 없으면 닫힘 */
  const [editing, setEditing] = useState<SupplierContact | 'new'>()
  const [saving, setSaving] = useState(false)
  const contacts = useQuery({ queryKey: ['supplier-contacts', supplierId], queryFn: () => fetchSupplierContacts(supplierId) })

  /** 담당자 목록과, 담당자 변경이 남는 변경 이력을 함께 새로 고친다. */
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ['supplier-contacts', supplierId] }),
    queryClient.invalidateQueries({ queryKey: ['changes'] }),
  ])

  /** 입력 창을 연다. 수정이면 기존 값을 채운다. */
  const openForm = (target: SupplierContact | 'new') => {
    form.resetFields()
    if (target !== 'new') {
      form.setFieldsValue({ name: target.name, title: target.title ?? undefined, phone: target.phone ?? undefined, email: target.email ?? undefined })
    }
    setEditing(target)
  }

  /** 담당자를 추가하거나 수정한다. 서버가 돌려준 입력 오류는 해당 칸 아래에 보여 준다. */
  const save = async (values: SupplierContactRequest) => {
    setSaving(true)
    try {
      if (editing === 'new') await createSupplierContact(supplierId, values)
      else if (editing) await updateSupplierContact(supplierId, editing.id, values)
      setEditing(undefined)
      await refresh()
      message.success('담당자를 저장했습니다.')
    } catch (e) {
      const problem = toProblem(e)
      if (problem.errors?.length) form.setFields(problem.errors.map((f) => ({ name: f.field as keyof SupplierContactRequest, errors: [f.message] })))
      else message.error(problem.detail)
    } finally {
      setSaving(false)
    }
  }

  /** 확인받은 뒤 담당자를 삭제한다. */
  const confirmDelete = (contact: SupplierContact) => {
    modal.confirm({
      title: '이 담당자를 삭제하시겠습니까?', content: contact.title ? `${contact.name} (${contact.title})` : contact.name,
      okText: '삭제', okButtonProps: { danger: true }, cancelText: '취소',
      onOk: async () => {
        try {
          await deleteSupplierContact(supplierId, contact.id)
          await refresh()
          message.success('담당자를 삭제했습니다.')
        } catch (e) {
          message.error(toProblem(e).detail)
        }
      },
    })
  }

  return (
    <Flex vertical gap={12}>
      <Flex justify="space-between" align="center">
        <Typography.Text type="secondary">기본정보의 업체 담당자 외에 연락하는 분을 등록합니다.</Typography.Text>
        {canEdit && <Button type="primary" icon={<PlusOutlined />} onClick={() => openForm('new')}>담당자 추가</Button>}
      </Flex>
      {contacts.isError && <Alert type="error" showIcon title={toProblem(contacts.error).detail} />}
      <Table<SupplierContact> rowKey="id" size="small" dataSource={contacts.data} loading={contacts.isLoading} pagination={false}
        scroll={{ x: 760 }} locale={{ emptyText: '등록된 담당자가 없습니다.' }}
        columns={[
          { title: '이름', dataIndex: 'name', width: 140 },
          { title: '직책', dataIndex: 'title', width: 180, render: (v) => v ?? '-' },
          { title: '전화번호', dataIndex: 'phone', width: 160, className: 'num', render: (v) => v ?? '-' },
          { title: '이메일', dataIndex: 'email', render: (v) => v ? <a href={`mailto:${v}`}>{v}</a> : '-' },
          ...(canEdit ? [{
            title: '작업', width: 150,
            render: (_: unknown, row: SupplierContact) => (
              <Flex gap={4}>
                <Button size="small" icon={<EditOutlined />} onClick={() => openForm(row)}>수정</Button>
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDelete(row)}>삭제</Button>
              </Flex>
            ),
          }] : []),
        ]} />
      <Modal open={!!editing} title={editing === 'new' ? '담당자 추가' : '담당자 수정'} destroyOnHidden
        okText="저장" cancelText="취소" confirmLoading={saving} onOk={() => form.submit()} onCancel={() => !saving && setEditing(undefined)}>
        <Form form={form} layout="vertical" onFinish={save} disabled={saving}>
          <Form.Item name="name" label="이름" rules={[{ required: true, whitespace: true, message: '이름을 입력해 주세요.' }, { max: 50 }]}>
            <Input maxLength={50} autoFocus />
          </Form.Item>
          <Form.Item name="title" label="직책" rules={[{ max: 50 }]}>
            <Input maxLength={50} placeholder="예: 품질팀 과장" />
          </Form.Item>
          <Form.Item name="phone" label="전화번호" rules={[{ max: 30 }]}>
            <Input maxLength={30} placeholder="예: 010-0000-0000" />
          </Form.Item>
          <Form.Item name="email" label="이메일" rules={[{ type: 'email', message: '이메일 형식이 올바르지 않습니다.' }, { max: 100 }]}>
            <Input maxLength={100} />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  )
}
