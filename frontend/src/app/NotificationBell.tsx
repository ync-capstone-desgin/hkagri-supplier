import { Badge, Button, Empty, Flex, Popover, Typography } from 'antd'
import { BellOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { fetchInbox, fetchUnreadCount } from '../api/notifications'
import { DdayBadge } from '../components/DdayBadge'
import { useNotificationStream } from '../hooks/useNotificationStream'

/**
 * 상단 🔔 알림 버튼.
 * 미확인 알림 수를 배지로 보여주고, 누르면 미확인 알림 최근 5건을 미리 보여준다.
 * 실시간 연결(SSE)도 여기서 연다. 새 알림이 오면 배지 숫자가 바로 바뀐다.
 */
export function NotificationBell() {
  const navigate = useNavigate()
  useNotificationStream(true)
  const { data: count = 0 } = useQuery({ queryKey: ['notifications', 'unread'], queryFn: fetchUnreadCount, refetchInterval: 60_000 })
  const { data: preview } = useQuery({ queryKey: ['notifications', 'inbox', 'UNREAD'], queryFn: () => fetchInbox('UNREAD') })

  const content = (
    <div style={{ width: 340 }}>
      {preview?.content.length ? (
        <div>
          {preview.content.slice(0, 5).map((n) => (
            <Flex key={n.id} gap={8} align="center" onClick={() => navigate('/notifications')}
              style={{ cursor: 'pointer', padding: '8px 4px', borderBottom: '1px solid #f0f0f0' }}>
              <DdayBadge daysLeft={n.daysLeft} status={n.daysLeft != null && n.daysLeft < 0 ? 'EXPIRED' : 'IMMINENT'} />
              <Typography.Text ellipsis style={{ flex: 1 }}>{n.title.replace(/^\[[^\]]+\]\s*/, '')}</Typography.Text>
            </Flex>
          ))}
        </div>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="새 알림이 없습니다." />
      )}
      <Button type="link" block onClick={() => navigate('/notifications')}>알림함 전체 보기</Button>
    </div>
  )

  return (
    <Popover content={content} title={`미확인 알림 ${count}건`} trigger="click" placement="bottomRight">
      <Badge count={count} overflowCount={99} size="small">
        <Button type="text" shape="circle" icon={<BellOutlined style={{ fontSize: 18 }} />} aria-label={`알림 ${count}건`} />
      </Badge>
    </Popover>
  )
}
