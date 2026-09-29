import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { Spin } from 'antd'
import { useAuth } from './AuthContext'

/**
 * 로그인한 사용자만 볼 수 있는 화면을 감싼다.
 * 로그인 여부 확인 중에는 로딩 표시, 로그인 전이면 로그인 화면으로 보낸다.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth()
  if (loading) {
    return <Spin size="large" fullscreen />
  }
  if (!me) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}
