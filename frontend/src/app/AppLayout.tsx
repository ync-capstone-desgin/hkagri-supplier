import { Avatar, Dropdown, Layout, Menu, Tabs, Tag, Typography } from 'antd'
import { IdcardOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons'
import { Outlet, useNavigate } from 'react-router'
import { useAuth } from './AuthContext'
import { ClockIndicator } from './ClockIndicator'
import { NotificationBell } from './NotificationBell'
import { findMenu, MENU } from './menu'
import { useTabs } from './TabsContext'
import { BRAND } from './theme'
import { useCodes } from '../hooks/useCodes'

const { Header, Sider, Content } = Layout

/**
 * 로그인 후 공통 화면 틀.
 * 상단: 시스템 이름과 사용자 메뉴 / 좌측: 업무 메뉴 / 본문 위: 열린 작업 탭 / 본문: 현재 화면
 */
export function AppLayout() {
  const navigate = useNavigate()
  const { me, logout } = useAuth()
  const { label } = useCodes()
  const { tabs, activePath, closeTab } = useTabs()
  const activeMenu = findMenu(activePath)

  /** 로그아웃 후 로그인 화면으로 이동한다. */
  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ height: '100%' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 12, borderBottom: '1px solid #e5e7eb' }}>
        <img src="/favicon.svg" width={28} height={28} alt="" />
        <Typography.Text strong style={{ fontSize: 17, color: BRAND, whiteSpace: 'nowrap' }}>
          협력업체 통합관리
        </Typography.Text>
        <Tag color="green" style={{ marginInlineStart: 4 }}>
          PoC
        </Tag>
        <div style={{ flex: 1 }} />
        <ClockIndicator />
        <NotificationBell />
        <Dropdown
          menu={{
            items: [
              { key: 'mypage', icon: <IdcardOutlined />, label: '마이페이지', onClick: () => navigate('/mypage') },
              { type: 'divider' },
              { key: 'logout', icon: <LogoutOutlined />, label: '로그아웃', onClick: handleLogout },
            ],
          }}
        >
          <span style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
            <Avatar size="small" icon={<UserOutlined />} style={{ background: BRAND }} />
            <span style={{ whiteSpace: 'nowrap' }}>
              {me?.name} <Typography.Text type="secondary">({label('role', me?.role)})</Typography.Text>
            </span>
          </span>
        </Dropdown>
      </Header>
      <Layout>
        {/* 화면이 좁으면(1200px 미만) 메뉴를 아이콘만 남기고 접는다. */}
        <Sider width={200} breakpoint="xl" collapsedWidth={64} style={{ borderRight: '1px solid #e5e7eb' }}>
          <Menu
            mode="inline"
            selectedKeys={activeMenu ? [activeMenu.path] : []}
            style={{ borderInlineEnd: 'none', paddingTop: 8 }}
            onClick={({ key }) => navigate(key)}
            items={MENU.map((m) => ({
              key: m.path,
              icon: m.icon,
              disabled: m.planned,
              label: (
                <span>
                  {m.label}
                  {m.planned && (
                    <Tag variant="filled" style={{ marginInlineStart: 6, fontSize: 11 }}>
                      예정
                    </Tag>
                  )}
                </span>
              ),
            }))}
          />
        </Sider>
        <Content style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          <Tabs
            type="editable-card"
            hideAdd
            size="small"
            activeKey={activePath}
            onChange={(key) => navigate(key)}
            onEdit={(key, action) => action === 'remove' && closeTab(String(key))}
            items={tabs.map((t) => ({ key: t.path, label: t.title, closable: true }))}
            tabBarStyle={{ margin: 0, padding: '6px 12px 0', background: '#fff' }}
          />
          <div style={{ flex: 1, overflow: 'auto', padding: 16 }}>
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}
