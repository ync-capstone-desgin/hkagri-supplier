import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { Link, Navigate, useSearchParams } from 'react-router'
import { resetPassword } from '../api/auth'
import { toProblem } from '../api/client'
import { useAuth } from '../app/AuthContext'

/** 입력값: 새 비밀번호와 확인 */
interface ResetForm {
  newPassword: string
  newPasswordConfirm: string
}

/**
 * 비밀번호 재설정 화면. 메일 속 링크(?token=...)로 들어온다.
 * 토큰이 없거나, 만료·이미 사용된 토큰이면 저장 시 오류로 안내한다.
 */
export function ResetPasswordPage() {
  const { me } = useAuth()
  const [params] = useSearchParams()
  const token = params.get('token')
  const [form] = Form.useForm<ResetForm>()
  const [saving, setSaving] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string>()

  /** 토큰과 새 비밀번호로 재설정을 요청한다. */
  const handleSubmit = async ({ newPassword }: ResetForm) => {
    if (!token) return
    setSaving(true)
    setError(undefined)
    try {
      await resetPassword(token, newPassword)
      setDone(true)
    } catch (e) {
      const problem = toProblem(e)
      // 토큰 오류(만료·이미 사용)는 입력칸이 아니라 토큰 자체의 문제이므로 화면 위 안내로 보여준다.
      const tokenError = problem.errors?.find((fe) => fe.field === 'token')
      if (tokenError) {
        setError(tokenError.message)
      } else if (problem.errors?.length) {
        form.setFields(problem.errors.map((fe) => ({ name: fe.field as keyof ResetForm, errors: [fe.message] })))
      } else {
        setError(problem.detail)
      }
    } finally {
      setSaving(false)
    }
  }

  if (me) return <Navigate to="/" replace />

  return (
    <div style={{ minHeight: '100%', display: 'grid', placeItems: 'center', padding: 16 }}>
      <Card style={{ width: '100%', maxWidth: 380 }}>
        <Typography.Title level={4}>비밀번호 재설정</Typography.Title>
        {!token ? (
          <Alert type="error" showIcon title="유효하지 않은 링크입니다."
            description="메일에 있는 링크를 다시 눌러 주세요. 그래도 안 되면 비밀번호 찾기를 다시 시도해 주세요." />
        ) : done ? (
          <Alert type="success" showIcon title="비밀번호를 바꿨습니다." description="새 비밀번호로 로그인해 주세요." />
        ) : (
          <>
            <Typography.Paragraph type="secondary">새로 쓸 비밀번호를 입력해 주세요.</Typography.Paragraph>
            {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }} />}
            <Form form={form} layout="vertical" onFinish={handleSubmit} disabled={saving} requiredMark={false}>
              <Form.Item name="newPassword" label="새 비밀번호" rules={[
                { required: true, whitespace: true, message: '새 비밀번호를 입력해 주세요.' },
                { min: 8, max: 64, message: '비밀번호는 8~64자로 입력해 주세요.' },
              ]}>
                <Input.Password maxLength={64} autoFocus autoComplete="new-password" />
              </Form.Item>
              <Form.Item name="newPasswordConfirm" label="새 비밀번호 확인" dependencies={['newPassword']} rules={[
                { required: true, message: '새 비밀번호 확인을 입력해 주세요.' },
                ({ getFieldValue }) => ({
                  /** 새 비밀번호가 바뀌면 확인 입력값도 다시 검증한다. */
                  validator(_, value) {
                    return !value || value === getFieldValue('newPassword')
                      ? Promise.resolve()
                      : Promise.reject(new Error('비밀번호가 일치하지 않습니다.'))
                  },
                }),
              ]}>
                <Input.Password maxLength={64} autoComplete="new-password" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={saving}>비밀번호 변경</Button>
            </Form>
          </>
        )}
        <div style={{ marginTop: 16, textAlign: 'center' }}>
          <Link to={token ? '/login' : '/forgot-password'}>{token ? '로그인으로 돌아가기' : '비밀번호 찾기로 이동'}</Link>
        </div>
      </Card>
    </div>
  )
}
