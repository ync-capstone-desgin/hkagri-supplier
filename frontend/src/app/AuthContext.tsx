import { createContext, useContext, useEffect, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchMe, login as apiLogin, logout as apiLogout } from '../api/auth'
import { api, isUnauthorized } from '../api/client'
import type { Me } from '../api/types'

/** 로그인 상태와 로그인·로그아웃 동작을 담는 컨텍스트 값 */
interface AuthValue {
  /** 로그인한 사용자. 로그인 전이면 null */
  me: Me | null
  /** 로그인 여부를 서버에 확인하는 중인지 */
  loading: boolean
  /** 등록·수정 권한(관리자·담당자)이 있는지 */
  canEdit: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

/**
 * 로그인 상태 제공자.
 * 앱 시작 시 /api/auth/me로 세션이 살아 있는지 확인하고, 결과를 모든 화면에 공유한다.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const { data, isLoading } = useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await fetchMe()
      } catch {
        return null
      }
    },
    staleTime: Infinity,
  })

  // 사용 중 세션이 만료되어 API가 401을 돌려주면 로그인 상태를 비워 로그인 화면으로 보낸다.
  useEffect(() => {
    const id = api.interceptors.response.use(undefined, (error) => {
      if (isUnauthorized(error) && !String(error.config?.url).startsWith('/auth/')) {
        queryClient.setQueryData(['me'], null)
      }
      return Promise.reject(error)
    })
    return () => api.interceptors.response.eject(id)
  }, [queryClient])

  /** 로그인하고, 성공하면 로그인 사용자 정보를 갱신한다. 실패 시 오류를 호출한 화면으로 넘긴다. */
  const login = async (username: string, password: string) => {
    const me = await apiLogin(username, password)
    queryClient.setQueryData(['me'], me)
  }

  /**
   * 로그아웃하고, 다른 사용자가 이전 데이터를 보지 않도록 캐시를 비운다.
   * 로그인 사용자(me)를 먼저 null로 바꿔 화면에 알린 뒤 나머지 캐시만 지운다.
   * (clear()로 me까지 지우면 화면이 변경을 알지 못해 새로고침 전까지 로그인 상태로 남는다)
   */
  const logout = async () => {
    await apiLogout()
    queryClient.setQueryData(['me'], null)
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'me' })
  }

  const me = data ?? null
  const value: AuthValue = { me, loading: isLoading, canEdit: me?.role === 'ADMIN' || me?.role === 'MANAGER', login, logout }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** 현재 로그인 상태를 사용하는 훅. AuthProvider 안에서만 쓸 수 있다. */
export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth는 AuthProvider 안에서 사용해야 합니다.')
  return value
}
