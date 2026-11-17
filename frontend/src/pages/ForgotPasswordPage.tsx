import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { Link, Navigate } from 'react-router'
import { forgotPassword } from '../api/auth'
import { toProblem } from '../api/client'
import { useAuth } from '../app/AuthContext'

/** 입력값: 아이디 또는 이메일 */
interface ForgotForm {
  usernameOrEmail: string
}

/**
 * 비밀번호 찾기 화면.
 * 계정이 있든 없든 서버가 항상 같은 안내를 돌려주므로, 화면도 그 문구를 그대로 보여준다.
 */
export function ForgotPasswordPage() {
  const { me } = useAuth()
  const [form] = Form.useForm<ForgotForm>()
  const [saving, setSaving] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string>()

  /** 재설정 메일을 요청하고, 성공하면 서버 안내 문구를 보여준다. */
  const handleSubmit = async ({ usernameOrEmail }: ForgotForm) => {
    setSaving(true)
    setError(undefined)
    try {
      await forgotPassword(usernameOrEmail)
      setSent(true)
    } catch (e) {
      setError(toProblem(e).detail)
    } finally {
      setSaving(false)
    }
  }

  if (me) return <Navigate to="/" replace />

  return (
    <div style={{ minHeight: '100%', display: 'grid', placeItems: 'center', padding: 16 }}>
      <Card style={{ width: '100%', maxWidth: 380 }}>
        <Typography.Title level={4}>비밀번호 찾기</Typography.Title>
        {sent ? (
          <Alert type="success" showIcon title="메일을 확인해 주세요."
            description="해당 계정이 있으면 비밀번호 재설정 메일을 보냈습니다. 메일 속 링크는 30분 동안, 한 번만 사용할 수 있습니다." />
        ) : (
          <>
            <Typography.Paragraph type="secondary">가입할 때 등록한 아이디 또는 이메일을 입력해 주세요.</Typography.Paragraph>
            {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }} />}
            <Form form={form} layout="vertical" onFinish={handleSubmit} disabled={saving} requiredMark={false}>
              <Form.Item name="usernameOrEmail" label="아이디 또는 이메일" rules={[{ required: true, whitespace: true, message: '아이디 또는 이메일을 입력해 주세요.' }]}>
                <Input autoFocus autoComplete="username" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={saving}>재설정 메일 보내기</Button>
            </Form>
          </>
        )}
        <div style={{ marginTop: 16, textAlign: 'center' }}><Link to="/login">로그인으로 돌아가기</Link></div>
      </Card>
    </div>
  )
}
