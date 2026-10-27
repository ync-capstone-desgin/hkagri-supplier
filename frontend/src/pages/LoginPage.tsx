import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../app/AuthContext'
import { toProblem } from '../api/client'
import { BRAND } from '../app/theme'

/** 로그인 입력값 */
interface LoginForm {
  username: string
  password: string
}

/**
 * 로그인 화면.
 * 실패하면 비밀번호 칸을 비우고 서버 안내 문구를 보여준다. 이미 로그인했다면 첫 화면으로 보낸다.
 */
export function LoginPage() {
  const navigate = useNavigate()
  const { me, login } = useAuth()
  const [form] = Form.useForm<LoginForm>()
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  if (me) {
    return <Navigate to="/" replace />
  }

  /** 로그인을 시도하고, 성공하면 첫 화면으로 이동한다. */
  const handleSubmit = async ({ username, password }: LoginForm) => {
    setSubmitting(true)
    setError(undefined)
    try {
      await login(username, password)
      navigate('/', { replace: true })
    } catch (e) {
      setError(toProblem(e).detail)
      form.setFieldValue('password', '')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ minHeight: '100%', display: 'grid', placeItems: 'center', padding: 16 }}>
      <Card style={{ width: '100%', maxWidth: 380 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <img src="/favicon.svg" width={44} height={44} alt="" />
          <Typography.Title level={4} style={{ margin: '12px 0 4px', color: BRAND }}>
            협력업체 통합관리
          </Typography.Title>
          <Typography.Text type="secondary">계약·인증 만료를 놓치지 않도록</Typography.Text>
        </div>
        {error && <Alert type="error" title={error} showIcon style={{ marginBottom: 16 }} />}
        <Form form={form} layout="vertical" onFinish={handleSubmit} requiredMark={false}>
          <Form.Item name="username" label="아이디" rules={[{ required: true, message: '아이디를 입력해 주세요.' }]}>
            <Input prefix={<UserOutlined />} size="large" autoFocus autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="비밀번호" rules={[{ required: true, message: '비밀번호를 입력해 주세요.' }]}>
            <Input.Password prefix={<LockOutlined />} size="large" autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
            로그인
          </Button>
        </Form>
        <div style={{ marginTop: 16, textAlign: 'center' }}><Link to="/signup">회원가입</Link></div>
        <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0, fontSize: 12 }}>
          시연 계정: admin · manager1 · manager2 · viewer (비밀번호는 README 참고)
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
