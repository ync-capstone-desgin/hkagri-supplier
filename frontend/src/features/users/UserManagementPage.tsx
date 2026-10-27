import { useState } from 'react'
import { Alert, App, Badge, Button, Card, Modal, Select, Space, Table, Tabs, Tag, Typography } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../app/AuthContext'
import { changeUser, fetchAdminUsers, type AdminUser, type UserAction } from '../../api/userAdmin'
import { toProblem } from '../../api/client'
import type { Role } from '../../api/types'
import { formatDateTime } from '../../components/format'

const roleNames: Record<Role, string> = { ADMIN: '관리자', MANAGER: '담당자', VIEWER: '조회자' }
const statusNames = { PENDING: '승인 대기', ACTIVE: '사용', REJECTED: '거절', INACTIVE: '비활성' }
const actionNames: Record<UserAction, string> = {
  APPROVE: '승인', REJECT: '거절', CHANGE_ROLE: '역할 변경', DEACTIVATE: '비활성화', REACTIVATE: '다시 사용',
}

/** 가입 승인·거절과 계정 권한·상태를 관리하는 관리자 전용 화면. */
export function UserManagementPage() {
  const { me } = useAuth()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState('pending')
  const [editing, setEditing] = useState<{ user: AdminUser; action: 'APPROVE' | 'CHANGE_ROLE' }>()
  const [role, setRole] = useState<Role>('VIEWER')
  const [saving, setSaving] = useState(false)
  const isAdmin = me?.role === 'ADMIN'
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-users'], queryFn: fetchAdminUsers, enabled: isAdmin, staleTime: 10_000, refetchInterval: 30_000,
  })

  /** 변경 성공 후 사용자 목록과 담당자 선택 목록을 갱신한다. 실패해도 최신 목록을 다시 읽는다. */
  const save = async (user: AdminUser, action: UserAction, selectedRole?: Role) => {
    setSaving(true)
    try {
      await changeUser(user.id, action, selectedRole)
      await queryClient.invalidateQueries({ queryKey: ['users'] })
      setEditing(undefined)
      message.success(`${user.name} 님의 ${actionNames[action]} 처리가 완료되었습니다.`)
    } catch (e) {
      message.error(toProblem(e).detail)
    } finally {
      await queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setSaving(false)
    }
  }

  /** 승인·역할 변경은 역할을 고르고, 나머지는 대상과 작업을 확인한 뒤 실행한다. */
  const act = (user: AdminUser, action: UserAction) => {
    if (action === 'APPROVE' || action === 'CHANGE_ROLE') {
      setRole(user.role)
      setEditing({ user, action })
    } else {
      modal.confirm({
        title: `${user.name} (${user.username}) 님을 ${actionNames[action]}하시겠습니까?`,
        content: action === 'DEACTIVATE' ? '로그인과 이후 요청이 차단됩니다. 담당 업체·계약은 그대로 유지됩니다.' : undefined,
        okText: actionNames[action], cancelText: '취소',
        onOk: () => save(user, action),
      })
    }
  }

  if (!isAdmin) return <Alert type="error" showIcon title="관리자만 사용자 관리에 접근할 수 있습니다." />

  return (
    <Card title="사용자 관리" extra={<Button onClick={() => refetch()}>새로고침</Button>}>
      <Typography.Paragraph type="secondary">승인할 때 역할을 선택해 주세요. 자기 계정의 역할과 상태는 변경할 수 없습니다.</Typography.Paragraph>
      {error && <Alert type="error" showIcon title={toProblem(error).detail} style={{ marginBottom: 16 }} />}
      <Tabs activeKey={tab} onChange={setTab} items={[
        { key: 'pending', label: <Space>승인 대기<Badge count={data?.pendingCount ?? 0} showZero /></Space> },
        { key: 'all', label: '전체 사용자' },
      ]} />
      <Table<AdminUser> rowKey="id" loading={isLoading} scroll={{ x: 1000 }}
        dataSource={data?.users.filter((u) => tab === 'all' || u.status === 'PENDING')}
        pagination={{ pageSize: 10 }} columns={[
          { title: '이름 / 아이디', key: 'name', render: (_, u) => <><strong>{u.name}</strong><br />{u.username}</> },
          { title: '메일', dataIndex: 'email' },
          { title: '역할', dataIndex: 'role', width: 90, render: (value: Role) => <span style={{ whiteSpace: 'nowrap' }}>{roleNames[value]}</span> },
          { title: '상태', dataIndex: 'status', render: (value: AdminUser['status']) => <Tag color={value === 'PENDING' ? 'orange' : value === 'ACTIVE' ? 'green' : 'default'}>{statusNames[value]}</Tag> },
          { title: '가입 사유', dataIndex: 'signupReason', width: 180, render: (value) => <span style={{ overflowWrap: 'anywhere' }}>{value || '-'}</span> },
          { title: '가입일', dataIndex: 'createdAt', render: formatDateTime },
          { title: '관리', key: 'actions', width: 200, render: (_, u) => u.id === me?.id ? <Tag>본인</Tag> : (
            <Space wrap>
              {(u.status === 'PENDING' ? ['APPROVE', 'REJECT'] : u.status === 'ACTIVE' ? ['CHANGE_ROLE', 'DEACTIVATE'] : u.status === 'INACTIVE' ? ['REACTIVATE'] : []).map((action) => (
                <Button key={action} size="small" disabled={saving} danger={action === 'DEACTIVATE' || action === 'REJECT'}
                  onClick={() => act(u, action as UserAction)}>{actionNames[action as UserAction]}</Button>
              ))}
            </Space>
          ) },
        ]} />
      <Modal title={editing ? `${editing.user.name} 님 ${actionNames[editing.action]}` : ''} open={!!editing}
        okText={editing ? actionNames[editing.action] : '저장'} cancelText="취소" confirmLoading={saving}
        onCancel={() => !saving && setEditing(undefined)} onOk={() => editing && save(editing.user, editing.action, role)}>
        <Typography.Paragraph>적용할 역할을 선택해 주세요.</Typography.Paragraph>
        <Select aria-label="적용할 역할" value={role} onChange={setRole} style={{ width: '100%' }}
          options={Object.entries(roleNames).map(([value, label]) => ({ value, label }))} />
      </Modal>
    </Card>
  )
}
