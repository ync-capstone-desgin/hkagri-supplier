import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { Link, Navigate } from 'react-router'
import { signup, type SignupRequest } from '../api/auth'
import { toProblem } from '../api/client'
import { useAuth } from '../app/AuthContext'

/** 회원가입 신청 화면. 성공 후 승인 대기를 안내하며 자동 로그인하지 않는다. */
export function SignupPage() {
  const { me } = useAuth()
  const [form] = Form.useForm<SignupRequest>()
  const [saving, setSaving] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string>()

  /** 가입을 신청하고 서버의 입력 오류는 해당 입력칸 아래에 표시한다. */
  const handleSubmit = async (values: SignupRequest) => {
    setSaving(true)
    setError(undefined)
    try {
      await signup(values)
      form.resetFields()
      setDone(true)
    } catch (e) {
      const problem = toProblem(e)
      if (problem.errors?.length) {
        form.setFields(problem.errors.map((fe) => ({ name: fe.field as keyof SignupRequest, errors: [fe.message] })))
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
      <Card style={{ width: '100%', maxWidth: 440 }}>
        <Typography.Title level={3}>회원가입 신청</Typography.Title>
        {done ? (
          <Alert type="success" showIcon title="가입 신청이 완료되었습니다."
            description="승인 대기 중입니다. 관리자 승인 후 로그인하실 수 있습니다." />
        ) : (
          <>
            <Typography.Paragraph type="secondary">관리자 승인 후 이용하실 수 있습니다.</Typography.Paragraph>
            {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }} />}
            <Form form={form} layout="vertical" onFinish={handleSubmit} disabled={saving} requiredMark={false}>
              <Form.Item name="name" label="이름" rules={[{ required: true, whitespace: true, message: '이름을 입력해 주세요.' }]}>
                <Input maxLength={50} autoComplete="name" />
              </Form.Item>
              <Form.Item name="username" label="아이디" rules={[
                { required: true, message: '아이디를 입력해 주세요.' },
                { pattern: /^[a-zA-Z0-9_]{3,50}$/, message: '영문·숫자·밑줄 3~50자로 입력해 주세요.' },
              ]}>
                <Input maxLength={50} autoComplete="username" />
              </Form.Item>
              <Form.Item name="email" label="메일" rules={[
                { required: true, message: '메일을 입력해 주세요.' },
                { type: 'email', message: '올바른 메일 주소를 입력해 주세요.' },
              ]}>
                <Input maxLength={100} autoComplete="email" />
              </Form.Item>
              <Form.Item name="password" label="비밀번호" rules={[
                { required: true, whitespace: true, message: '비밀번호를 입력해 주세요.' },
                { min: 8, max: 64, message: '비밀번호는 8~64자로 입력해 주세요.' },
              ]}>
                <Input.Password maxLength={64} autoComplete="new-password" />
              </Form.Item>
              <Form.Item name="passwordConfirm" label="비밀번호 확인" dependencies={['password']} rules={[
                { required: true, message: '비밀번호 확인을 입력해 주세요.' },
                ({ getFieldValue }) => ({
                  /** 비밀번호가 변경되면 확인 입력값도 다시 검증한다. */
                  validator(_, value) {
                    return !value || value === getFieldValue('password')
                      ? Promise.resolve()
                      : Promise.reject(new Error('비밀번호가 일치하지 않습니다.'))
                  },
                }),
              ]}>
                <Input.Password maxLength={64} autoComplete="new-password" />
              </Form.Item>
              <Form.Item name="signupReason" label="가입 사유 (선택)">
                <Input.TextArea rows={3} maxLength={1000} showCount />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={saving}>가입 신청</Button>
            </Form>
          </>
        )}
        <div style={{ marginTop: 16, textAlign: 'center' }}><Link to="/login">로그인으로 돌아가기</Link></div>
      </Card>
    </div>
  )
}
