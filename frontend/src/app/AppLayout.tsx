import { useState } from 'react'
import { Avatar, Badge, Button, Drawer, Dropdown, Grid, Layout, Menu, Tabs, Tag, Typography, type MenuProps } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { fetchAdminUsers } from '../api/userAdmin'
import { LogoutOutlined, MenuOutlined, UserOutlined } from '@ant-design/icons'
import { Outlet, useNavigate } from 'react-router'
import { useAuth } from './AuthContext'
import { ClockIndicator } from './ClockIndicator'
import { NotificationBell } from './NotificationBell'
import { findMenu, MENU } from './menu'
import { useTabs } from './TabsContext'
import { BRAND } from './theme'
import { useCodes } from '../hooks/useCodes'

const { Header, Sider, Content } = Layout

/** 휴대폰에서 메뉴를 서랍으로 열고, 항목을 선택하면 닫는다. */
function MobileMenu({ items, selectedPath }: { items: MenuProps['items']; selectedPath?: string }) {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  return <>
    <Button type="text" icon={<MenuOutlined />} aria-label="메뉴 열기" aria-expanded={open}
      onClick={() => setOpen(true)} />
    <Drawer title="업무 메뉴" placement="left" size={280} open={open} onClose={() => setOpen(false)}
      styles={{ body: { padding: 0 } }}>
      <Menu mode="inline" selectedKeys={selectedPath ? [selectedPath] : []} items={items}
        onClick={({ key }) => { setOpen(false); navigate(key) }} />
    </Drawer>
  </>
}

/**
 * 로그인 후 공통 화면 틀.
 * 상단: 시스템 이름과 사용자 메뉴 / 좌측: 업무 메뉴 / 본문 위: 열린 작업 탭 / 본문: 현재 화면
 */
export function AppLayout() {
  const navigate = useNavigate()
  const { me, logout } = useAuth()
  const { label } = useCodes()
  const { tabs, activePath, closeTab } = useTabs()
  const screens = Grid.useBreakpoint()
  const mobile = screens.md === false
  const activeMenu = findMenu(activePath)
  const { data: adminUsers } = useQuery({
    queryKey: ['admin-users'], queryFn: fetchAdminUsers, enabled: me?.role === 'ADMIN', staleTime: 10_000, refetchInterval: 30_000,
  })

  /** 로그아웃 후 로그인 화면으로 이동한다. */
  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const menuItems = MENU.filter((m) => !m.adminOnly || me?.role === 'ADMIN').map((m) => ({
    key: m.path, icon: m.icon, disabled: m.planned,
    label: <span>
      {m.label}
      {m.path === '/users' && <Badge count={adminUsers?.pendingCount ?? 0} style={{ marginInlineStart: 6 }} />}
      {m.planned && <Tag variant="filled" style={{ marginInlineStart: 6, fontSize: 11 }}>예정</Tag>}
    </span>,
  }))

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        {mobile && <MobileMenu key={activePath} items={menuItems} selectedPath={activeMenu?.path} />}
        <img className="app-logo" src="/favicon.svg" width={28} height={28} alt="" />
        <Typography.Text className="app-brand" strong style={{ color: BRAND }}>
          협력업체 통합관리
        </Typography.Text>
        <Tag className="app-demo-tag" color="green" style={{ marginInlineStart: 4 }}>
          PoC
        </Tag>
        <div className="app-header-spacer" />
        <div className="app-clock"><ClockIndicator /></div>
        <NotificationBell mobile={mobile} />
        <Dropdown
          trigger={mobile ? ['click'] : ['hover', 'click']}
          menu={{
            items: [{ key: 'logout', icon: <LogoutOutlined />, label: '로그아웃', onClick: handleLogout }],
          }}
        >
          <button type="button" className="app-user-menu" aria-label={`사용자 메뉴: ${me?.name} (${label('role', me?.role)})`}>
            <Avatar size="small" icon={<UserOutlined />} style={{ background: BRAND }} />
            <span className="app-user-label" style={{ whiteSpace: 'nowrap' }}>
              {me?.name} <Typography.Text type="secondary">({label('role', me?.role)})</Typography.Text>
            </span>
          </button>
        </Dropdown>
      </Header>
      <Layout style={{ minHeight: 0 }}>
        {/* 768~1199px에서는 기존 아이콘 메뉴, 1200px 이상에서는 전체 메뉴를 표시한다. */}
        {!mobile && <Sider width={200} breakpoint="xl" collapsedWidth={64} style={{ borderRight: '1px solid #e5e7eb' }}>
          <Menu
            mode="inline"
            selectedKeys={activeMenu ? [activeMenu.path] : []}
            style={{ borderInlineEnd: 'none', paddingTop: 8 }}
            onClick={({ key }) => navigate(key)}
            items={menuItems}
          />
        </Sider>}
        <Content style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          {!mobile && <Tabs
            type="editable-card"
            hideAdd
            size="small"
            activeKey={activePath}
            onChange={(key) => navigate(key)}
            onEdit={(key, action) => action === 'remove' && closeTab(String(key))}
            items={tabs.filter((t) => !findMenu(t.path)?.adminOnly || me?.role === 'ADMIN').map((t) => ({ key: t.path, label: t.title, closable: true }))}
            tabBarStyle={{ margin: 0, padding: '6px 12px 0', background: '#fff' }}
          />}
          <div className="app-page">
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}
