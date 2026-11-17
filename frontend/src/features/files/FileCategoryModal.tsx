import { useState } from 'react'
import { Alert, App, Button, Flex, Form, Input, Modal, Space, Switch, Table, Tag, Typography } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toProblem } from '../../api/client'
import { changeFileCategoryActive, changeFileCategoryRequired, createFileCategory, fetchFileCategories, renameFileCategory, type FileCategory } from '../../api/files'
import { useAuth } from '../../app/AuthContext'

/**
 * 자료 검색 화면에서 여는 관리자 전용 자료 종류 관리 (#54). 인증 종류 관리와 같은 방식이다.
 * 이미 붙인 파일의 종류를 보존하려고 삭제는 두지 않고, 대신 사용 중지한다.
 */
export function FileCategoryModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { me } = useAuth()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<{ name: string }>()
  const [editing, setEditing] = useState<FileCategory>()
  const [saving, setSaving] = useState(false)
  const { data, isFetching, error, refetch } = useQuery({
    queryKey: ['file-categories', 'manage'], queryFn: fetchFileCategories, enabled: open && me?.role === 'ADMIN', staleTime: 0,
  })

  /** 종류 이름을 보여 주는 선택상자와 목록을 함께 새로 고친다. */
  const refresh = () => Promise.all(['file-categories', 'supplier-files', 'file-search', 'required-docs', 'suppliers', 'supplier']
    .map((key) => queryClient.invalidateQueries({ queryKey: [key] })))

  /** 새 종류를 추가하거나 이름을 바꾼다. 이름 오류는 입력칸 아래에 보여 준다. */
  const saveName = async ({ name }: { name: string }) => {
    setSaving(true)
    try {
      if (editing) await renameFileCategory(editing.id, name)
      else await createFileCategory(name)
      setEditing(undefined)
      form.resetFields()
      await refresh()
      message.success('자료 종류를 저장했습니다.')
    } catch (e) {
      const problem = toProblem(e)
      if (problem.errors?.some((field) => field.field === 'name')) {
        form.setFields([{ name: 'name', errors: problem.errors.filter((field) => field.field === 'name').map((field) => field.message) }])
      } else message.error(problem.detail)
    } finally {
      setSaving(false)
    }
  }

  /** 필수 서류 여부를 바로 바꾼다. 바꾸면 모든 업체의 서류 점검과 누락 건수가 다시 계산된다. */
  const toggleRequired = async (category: FileCategory, required: boolean) => {
    setSaving(true)
    try {
      await changeFileCategoryRequired(category.id, required)
      await refresh()
      message.success(required ? `${category.name}: 필수 서류로 정했습니다.` : `${category.name}: 필수 서류에서 뺐습니다.`)
    } catch (e) {
      message.error(toProblem(e).detail)
    } finally {
      setSaving(false)
    }
  }

  /** 이미 붙인 파일은 그대로라는 점을 안내한 뒤 사용 여부만 바꾼다. */
  const toggleActive = (category: FileCategory) => {
    modal.confirm({
      title: category.active ? '이 자료 종류를 사용 중지할까요?' : '이 자료 종류를 다시 사용할까요?',
      content: category.active
        ? `${category.name}: 이미 붙인 파일의 종류는 그대로 보이며, 새로 고를 수는 없습니다.`
        : `${category.name}: 다시 고를 수 있습니다.`,
      okText: category.active ? '사용 중지' : '다시 사용', cancelText: '취소',
      onOk: async () => {
        setSaving(true)
        try {
          await changeFileCategoryActive(category.id, !category.active)
          if (editing?.id === category.id) {
            setEditing(undefined)
            form.resetFields()
          }
          await refresh()
          message.success(category.active ? '사용 중지했습니다. 이미 붙인 파일의 종류는 유지됩니다.' : '다시 사용할 수 있습니다.')
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
    <Modal open={open} title="자료 종류 관리" width={780} destroyOnHidden
      onCancel={() => !saving && onClose()} closable={!saving} maskClosable={!saving}
      footer={<Button onClick={onClose} disabled={saving}>닫기</Button>}>
      <Typography.Paragraph type="secondary">
        업체 자료가 어떤 서류인지 구분하는 이름입니다. 쓰지 않는 종류는 사용 중지해도 이미 붙인 파일의 종류는 유지됩니다.
        <b> 필수</b>로 정한 종류는 업체마다 파일이 있는지 점검해, 빠진 업체를 협력업체 목록에서 모아 볼 수 있습니다.
      </Typography.Paragraph>
      <Form form={form} layout="vertical" onFinish={saveName} disabled={saving}>
        <Form.Item name="name" label={editing ? '자료 종류 이름 수정' : '새 자료 종류'}
          rules={[{ required: true, whitespace: true, message: '자료 종류 이름을 입력해 주세요.' },
            { max: 100, message: '이름은 100자 이내로 입력해 주세요.' }]}>
          <Input maxLength={100} placeholder="예: 거래명세서" />
        </Form.Item>
        <Flex gap={8} style={{ marginBottom: 16 }}>
          <Button type="primary" htmlType="submit" loading={saving}>{editing ? '이름 저장' : '종류 추가'}</Button>
          {editing && <Button onClick={() => { setEditing(undefined); form.resetFields() }}>수정 취소</Button>}
        </Flex>
      </Form>
      {error && <Alert type="error" showIcon title="자료 종류를 불러오지 못했습니다."
        action={<Button onClick={() => void refetch()}>다시 시도</Button>} style={{ marginBottom: 12 }} />}
      <Table<FileCategory> rowKey="id" size="small" dataSource={data} loading={isFetching}
        pagination={{ pageSize: 8, showSizeChanger: false }} columns={[
          { title: '자료 종류', dataIndex: 'name', render: (name: string) => <span style={{ overflowWrap: 'anywhere' }}>{name}</span> },
          { title: '필수 서류', key: 'required', width: 100, render: (_, category) => (
            <Switch size="small" checked={category.required} disabled={saving || !category.active} aria-label={`${category.name} 필수 서류`}
              onChange={(checked) => void toggleRequired(category, checked)} />
          ) },
          { title: '상태', key: 'active', width: 100, render: (_, category) => <Tag color={category.active ? 'green' : 'default'}>{category.active ? '사용' : '사용 중지'}</Tag> },
          { title: '관리', key: 'actions', width: 200, render: (_, category) => (
            <Space wrap>
              <Button size="small" disabled={saving} onClick={() => {
                setEditing(category)
                form.setFields([{ name: 'name', value: category.name, errors: [] }])
              }}>이름 수정</Button>
              <Button size="small" disabled={saving} onClick={() => toggleActive(category)}>{category.active ? '사용 중지' : '다시 사용'}</Button>
            </Space>
          ) },
        ]} />
    </Modal>
  )
}
