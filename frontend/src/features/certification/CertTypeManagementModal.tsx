import { useState } from 'react'
import { Alert, App, Button, Flex, Form, Input, Modal, Space, Table, Tag, Typography } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { changeCertTypeActive, createCertType, fetchCertTypes, updateCertType } from '../../api/certifications'
import { toProblem } from '../../api/client'
import type { CertType } from '../../api/types'
import { useAuth } from '../../app/AuthContext'

/** 인증 목록에서 여는 관리자 전용 종류 관리. 기존 기록을 보존하기 위해 삭제는 제공하지 않는다. */
export function CertTypeManagementModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { me } = useAuth()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<{ name: string }>()
  const [editing, setEditing] = useState<CertType>()
  const [saving, setSaving] = useState(false)
  const { data, isFetching, error, refetch } = useQuery({
    queryKey: ['cert-types'], queryFn: fetchCertTypes, enabled: open && me?.role === 'ADMIN', staleTime: 0,
    refetchOnWindowFocus: true,
  })

  /** 명칭을 표시하는 목록과 선택 상자를 함께 갱신한다. */
  const refresh = () => Promise.all([
    'cert-types', 'certifications', 'suppliers', 'supplier', 'expiries', 'expiryCalendar', 'renewalBoard', 'dashboard',
  ].map((key) => queryClient.invalidateQueries({ queryKey: [key] })))

  /** 이름 저장은 다른 관리자가 바꾼 사용 여부에 영향을 주지 않는다. */
  const saveName = async ({ name }: { name: string }) => {
    setSaving(true)
    try {
      if (editing) await updateCertType(editing.code, name)
      else await createCertType(name)
      setEditing(undefined)
      form.resetFields()
      await refresh()
      message.success('인증 종류를 저장했습니다.')
    } catch (e) {
      const problem = toProblem(e)
      if (problem.errors?.some((field) => field.field === 'name')) {
        form.setFields([{ name: 'name', errors: problem.errors.filter((field) => field.field === 'name').map((field) => field.message) }])
      } else message.error(problem.detail)
    } finally {
      setSaving(false)
    }
  }

  /** 기존 기록 보존과 신규 선택 제한을 안내한 뒤 사용 여부만 변경한다. */
  const toggleActive = (type: CertType) => {
    modal.confirm({
      title: type.active ? '이 인증 종류를 비활성화할까요?' : '이 인증 종류를 다시 사용할까요?',
      content: type.active
        ? `${type.name}: 기존 인증과 이력은 유지되며, 신규 등록·갱신에서는 선택할 수 없습니다.`
        : `${type.name}: 신규 등록·갱신에서 다시 선택할 수 있습니다.`,
      okText: type.active ? '비활성화' : '다시 사용', cancelText: '취소',
      onOk: async () => {
        setSaving(true)
        try {
          await changeCertTypeActive(type.code, !type.active)
          if (editing?.code === type.code) {
            setEditing(undefined)
            form.resetFields()
          }
          await refresh()
          message.success(type.active ? '비활성화했습니다. 기존 인증 기록은 유지됩니다.' : '다시 사용할 수 있습니다.')
        } catch (e) {
          message.error(toProblem(e).detail)
          throw e
        } finally {
          setSaving(false)
        }
      },
    })
  }

  if (me?.role !== 'ADMIN') return null

  return (
    <Modal open={open} title="인증 종류 관리" width={720} destroyOnHidden
      onCancel={() => !saving && onClose()} closable={!saving} maskClosable={!saving}
      footer={<Button onClick={onClose} disabled={saving}>닫기</Button>}>
      <Typography.Paragraph type="secondary">
        필요한 인증 종류를 직접 추가하세요. 사용하지 않는 종류는 비활성화해도 기존 인증과 갱신 이력이 유지됩니다.
      </Typography.Paragraph>
      <Form form={form} layout="vertical" onFinish={saveName} disabled={saving}>
        <Form.Item name="name" label={editing ? '인증 종류 이름 수정' : '새 인증 종류'}
          rules={[{ required: true, whitespace: true, message: '인증 종류 이름을 입력해 주세요.' },
            { max: 100, message: '이름은 100자 이내로 입력해 주세요.' }]}>
          <Input maxLength={100} placeholder="예: FSSC 22000" />
        </Form.Item>
        <Flex gap={8} style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" loading={saving}>{editing ? '이름 저장' : '종류 추가'}</Button>
          {editing && <Button onClick={() => { setEditing(undefined); form.resetFields() }}>수정 취소</Button>}
        </Flex>
      </Form>
      {error && <Alert type="error" showIcon title="인증 종류를 불러오지 못했습니다."
        action={<Button onClick={() => void refetch()}>다시 시도</Button>} style={{ marginBottom: 12 }} />}
      <Table<CertType> rowKey="code" size="small" dataSource={data} loading={isFetching}
        pagination={{ pageSize: 8, showSizeChanger: false }} columns={[
          { title: '인증 종류', dataIndex: 'name', render: (name: string) => <span style={{ overflowWrap: 'anywhere' }}>{name}</span> },
          { title: '상태', key: 'active', width: 90, render: (_, type) => <Tag color={type.active ? 'green' : 'default'}>{type.active ? '사용' : '비활성'}</Tag> },
          { title: '관리', key: 'actions', width: 200, render: (_, type) => (
            <Space wrap>
              <Button size="small" disabled={saving} onClick={() => {
                setEditing(type)
                form.setFields([{ name: 'name', value: type.name, errors: [] }])
              }}>이름 수정</Button>
              <Button size="small" disabled={saving} onClick={() => toggleActive(type)}>{type.active ? '비활성화' : '다시 사용'}</Button>
            </Space>
          ) },
        ]} />
    </Modal>
  )
}
