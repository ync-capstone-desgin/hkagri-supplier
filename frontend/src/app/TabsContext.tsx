import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { findMenu } from './menu'

/** 상단 작업 탭 하나 */
export interface WorkTab {
  /** 탭 경로 (예: /suppliers, /suppliers/3) */
  path: string
  title: string
}

interface TabsValue {
  tabs: WorkTab[]
  activePath: string
  closeTab: (path: string) => void
  setTitle: (path: string, title: string) => void
}

const TabsContext = createContext<TabsValue | null>(null)

/**
 * ERP식 다중 작업 탭(MDI) 관리자.
 * 메뉴나 링크로 화면을 열면 탭이 추가되고, 이미 열린 화면이면 그 탭으로 전환한다.
 * 각 화면의 데이터는 React Query가 캐시하므로 탭을 오가도 다시 불러오는 시간이 거의 없다.
 */
export function TabsProvider({ children }: { children: ReactNode }) {
  const location = useLocation()
  const navigate = useNavigate()
  const [tabs, setTabs] = useState<WorkTab[]>([])
  const activePath = location.pathname

  // 주소가 바뀌면 해당 화면의 탭이 없을 때 새로 연다.
  useEffect(() => {
    const menu = findMenu(activePath)
    if (!menu) return
    setTabs((prev) =>
      prev.some((t) => t.path === activePath)
        ? prev
        : [...prev, { path: activePath, title: activePath === menu.path ? menu.label : `${menu.label} 상세` }],
    )
  }, [activePath])

  /** 탭을 닫는다. 보고 있던 탭을 닫으면 바로 옆 탭으로 이동한다. */
  const closeTab = useCallback(
    (path: string) => {
      const index = tabs.findIndex((t) => t.path === path)
      const next = tabs.filter((t) => t.path !== path)
      setTabs(next)
      if (path === activePath) {
        const neighbor = next[Math.min(index, next.length - 1)]
        navigate(neighbor ? neighbor.path : '/suppliers')
      }
    },
    [tabs, activePath, navigate],
  )

  /** 탭 제목을 바꾼다. 상세 화면이 데이터를 받은 뒤 업체명 등으로 제목을 붙일 때 쓴다. */
  const setTitle = useCallback((path: string, title: string) => {
    setTabs((prev) => prev.map((t) => (t.path === path ? { ...t, title } : t)))
  }, [])

  return <TabsContext.Provider value={{ tabs, activePath, closeTab, setTitle }}>{children}</TabsContext.Provider>
}

/** 작업 탭 상태를 사용하는 훅 */
export function useTabs(): TabsValue {
  const value = useContext(TabsContext)
  if (!value) throw new Error('useTabs는 TabsProvider 안에서 사용해야 합니다.')
  return value
}

/** 현재 화면의 탭 제목을 지정하는 훅. 제목이 준비되면(값이 있으면) 반영한다. */
export function useTabTitle(title: string | undefined) {
  const { setTitle } = useTabs()
  const { pathname } = useLocation()
  useEffect(() => {
    if (title) setTitle(pathname, title)
  }, [title, pathname, setTitle])
}
