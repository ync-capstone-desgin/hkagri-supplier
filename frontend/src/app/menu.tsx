import type { ReactNode } from 'react'
import {
  AlertOutlined, AuditOutlined, BellOutlined, CalendarOutlined, DashboardOutlined, FileSearchOutlined, FileTextOutlined, FolderOpenOutlined, HistoryOutlined, ProjectOutlined, SettingOutlined, ShopOutlined, TeamOutlined,
} from '@ant-design/icons'

/** 좌측 메뉴 항목 하나 */
export interface MenuEntry {
  /** 이동할 경로 (탭의 식별자로도 쓴다) */
  path: string
  label: string
  icon: ReactNode
  /** 메뉴에서 이 항목이 속한 묶음의 구분 제목. 없으면 제목 없이 맨 위에 둔다. */
  group?: string
  /** 아직 개발 전인 메뉴. 전체 구성을 보여주기 위해 비활성 상태로 표시한다. */
  planned?: boolean
  /** 관리자에게만 표시하는 메뉴. 서버에서도 별도로 권한을 검사한다. */
  adminOnly?: boolean
}

/**
 * 좌측 메뉴 구성 (#65). 같은 묶음(group)의 항목은 이어서 적고, 적힌 순서대로 화면에 나온다.
 * 묶음은 접었다 펴지 않고 구분 제목만 붙인다. 메뉴가 늘어도 클릭 수가 늘지 않게 하기 위함이다.
 * '관리' 묶음은 모두 관리자 전용이라 담당자·조회자에게는 묶음째 보이지 않는다.
 */
export const MENU: MenuEntry[] = [
  { path: '/dashboard', label: '대시보드', icon: <DashboardOutlined /> },
  { path: '/suppliers', label: '협력업체', icon: <ShopOutlined />, group: '업체 관리' },
  { path: '/contracts', label: '계약 관리', icon: <FileTextOutlined />, group: '업체 관리' },
  { path: '/certifications', label: '인증 관리', icon: <AuditOutlined />, group: '업체 관리' },
  { path: '/expiries', label: '만료 예정', icon: <AlertOutlined />, group: '만료·갱신' },
  { path: '/expiry-calendar', label: '만료 캘린더', icon: <CalendarOutlined />, group: '만료·갱신' },
  { path: '/renewal-board', label: '갱신 현황판', icon: <ProjectOutlined />, group: '만료·갱신' },
  { path: '/forms', label: '양식 자료실', icon: <FolderOpenOutlined />, group: '자료' },
  { path: '/files', label: '자료 검색', icon: <FileSearchOutlined />, group: '자료' },
  { path: '/notifications', label: '알림함', icon: <BellOutlined />, group: '알림' },
  { path: '/settings', label: '알림 설정', icon: <SettingOutlined />, group: '관리', adminOnly: true },
  { path: '/users', label: '사용자 관리', icon: <TeamOutlined />, group: '관리', adminOnly: true },
  { path: '/changes', label: '변경 이력', icon: <HistoryOutlined />, group: '관리', adminOnly: true },
]

/** 경로에 맞는 메뉴 항목을 찾는다. 상세 경로(/suppliers/3)는 상위 메뉴(/suppliers)로 본다. */
export function findMenu(pathname: string): MenuEntry | undefined {
  return MENU.find((m) => pathname === m.path || pathname.startsWith(m.path + '/'))
}

/**
 * 좌측 메뉴(MENU)엔 없지만 상단 탭 제목은 필요한 화면.
 * 예: 마이페이지는 이름 드롭다운으로만 들어가므로 사이드바엔 안 보인다.
 */
export const EXTRA_TABS: Record<string, string> = {
  '/mypage': '마이페이지',
}
