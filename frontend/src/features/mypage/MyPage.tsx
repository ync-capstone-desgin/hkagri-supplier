import { useEffect } from 'react'
import { App, Button, Card, Descriptions, Flex, Form, Input, Switch } from 'antd'
import { LockOutlined, SaveOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toProblem } from '../../api/client'
import { changeMyPassword, fetchMyProfile, updateMyProfile } from '../../api/me'
import type { PasswordChangeRequest, ProfileUpdateRequest } from '../../api/types'
import { formatDateTime } from '../../components/format'
import { useCodes } from '../../hooks/useCodes'

/** 비밀번호 변경 폼 입력값. 새 비밀번호 확인 칸은 화면에서만 쓰고 서버에 보내지 않는다. */
interface PasswordFormValues extends PasswordChangeRequest {
  confirmPassword: string
}

/**
 * 마이페이지 (FR-028).
 * 1) 내 정보: 이름·이메일을 고치고, 알림메일 수신을 켜고 끈다. 이메일을 바꾸면 다음 알림부터 새 주소로 간다.
 * 2) 비밀번호 변경: 현재 비밀번호가 맞아야 바뀐다.
 * 3) 역할·가입일은 읽기 전용으로 보여준다.
 */
export function MyPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { label } = useCodes()
  const [profileForm] = Form.useForm<ProfileUpdateRequest>()
  const [passwordForm] = Form.useForm<PasswordFormValues>()
  const { data: profile } = useQuery({ queryKey: ['myProfile'], queryFn: fetchMyProfile })

  // 서버에서 받은 내 정보로 수정 폼 초깃값을 채운다.
  useEffect(() => {
    if (profile) profileForm.setFieldsValue({ name: profile.name, email: profile.email, notifyEmail: profile.notifyEmail })
  }, [profile, profileForm])

  /** 내 정보를 저장한다. 헤더의 이름 표시도 함께 갱신한다. */
  const saveProfile = async (values: ProfileUpdateRequest) => {
    try {
      await updateMyProfile(values)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['myProfile'] }),
        queryClient.invalidateQueries({ queryKey: ['me'] }),
      ])
      message.success('내 정보를 저장했습니다.')
    } catch (e) {
      message.error(toProblem(e).detail)
    }
  }

  /** 현재 비밀번호를 확인한 뒤 새 비밀번호로 바꾼다. */
  const savePassword = async (values: PasswordFormValues) => {
    try {
      await changeMyPassword({ currentPassword: values.currentPassword, newPassword: values.newPassword })
      passwordForm.resetFields()
      message.success('비밀번호를 바꿨습니다.')
    } catch (e) {
      message.error(toProblem(e).detail)
    }
  }

  if (!profile) return null

  return (
    <Flex vertical gap={12} style={{ maxWidth: 560 }}>
      <Card size="small" title="내 정보">
        <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="아이디">{profile.username}</Descriptions.Item>
          <Descriptions.Item label="역할">{label('role', profile.role)}</Descriptions.Item>
          <Descriptions.Item label="가입일">{formatDateTime(profile.createdAt)}</Descriptions.Item>
        </Descriptions>

        <Form form={profileForm} layout="vertical" onFinish={saveProfile}>
          <Form.Item name="name" label="이름" rules={[{ required: true, message: '이름을 입력해 주세요.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="email" label="이메일"
            rules={[{ required: true, message: '이메일을 입력해 주세요.' }, { type: 'email', message: '이메일 형식이 올바르지 않습니다.' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="notifyEmail" label="알림 메일 받기" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>저장</Button>
        </Form>
      </Card>

      <Card size="small" title="비밀번호 변경">
        <Form form={passwordForm} layout="vertical" onFinish={savePassword}>
          <Form.Item name="currentPassword" label="현재 비밀번호" rules={[{ required: true, message: '현재 비밀번호를 입력해 주세요.' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item name="newPassword" label="새 비밀번호"
            rules={[{ required: true, message: '새 비밀번호를 입력해 주세요.' }, { min: 4, message: '4자 이상 입력해 주세요.' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="confirmPassword" label="새 비밀번호 확인" dependencies={['newPassword']}
            rules={[
              { required: true, message: '새 비밀번호를 한 번 더 입력해 주세요.' },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('새 비밀번호가 서로 다릅니다.')),
              }),
            ]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" icon={<LockOutlined />}>비밀번호 변경</Button>
        </Form>
      </Card>
    </Flex>
  )
}
