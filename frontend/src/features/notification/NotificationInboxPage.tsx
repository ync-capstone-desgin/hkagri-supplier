import { useState } from 'react'
import { App, Button, Card, Empty, Flex, Segmented, Select, Skeleton, Tag, Tooltip, Typography } from 'antd'
import { CheckOutlined, CloseCircleOutlined, ClockCircleOutlined, MailOutlined, DesktopOutlined, ShopOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { toProblem } from '../../api/client'
import { changeNotificationAction, fetchInbox, markAllRead } from '../../api/notifications'
import type { ActionStatus, DeliveryItem, NotificationItem } from '../../api/types'
import { DdayBadge } from '../../components/DdayBadge'
import { formatDateTime } from '../../components/format'
import { useCodes } from '../../hooks/useCodes'

/** 처리상태 탭. ALL은 전체 */
type InboxTab = ActionStatus | 'ALL'

/** 처리상태별 태그 색 */
const ACTION_COLOR: Record<ActionStatus, string> = {
  UNREAD: 'red',
  READ: 'default',
  IN_PROGRESS: 'processing',
  DONE: 'success',
}

/** 채널 하나의 발송 결과를 작은 아이콘 표시로 보여준다. 실패·재시도면 사유를 말풍선으로 보여준다. */
function DeliveryMark({ d }: { d: DeliveryItem }) {
  const { label } = useCodes()
  const icon = d.channel === 'EMAIL' ? <MailOutlined /> : <DesktopOutlined />
  const state = d.status === 'SENT' ? { color: 'success', mark: <CheckOutlined />, text: '발송완료' }
    : d.status === 'FAILED' ? { color: 'error', mark: <CloseCircleOutlined />, text: '발송실패' }
      : { color: 'warning', mark: <ClockCircleOutlined />, text: d.retryCount > 0 ? `재시도 대기(${d.retryCount}회 실패)` : '발송 대기' }
  const tag = <Tag color={state.color} icon={icon} style={{ marginInlineEnd: 4 }}>{label('channel', d.channel)} {state.mark}</Tag>
  return <Tooltip title={d.lastError ? `${state.text} · ${d.lastError}` : state.text}>{tag}</Tooltip>
}

/**
 * 알림함 (FR-011).
 * 내게 온 만료 알림을 만료일이 가까운 순으로 보여주고, 처리상태(미확인 → 확인 → 처리중 → 완료)를 바꿀 수 있다.
 * [업체 보기]를 누르면 해당 업체 상세로 가며, 미확인 알림은 자동으로 "확인"이 된다.
 * 채널별 발송 결과(화면·이메일)도 함께 보여 알림이 실제로 전달됐는지 확인할 수 있다.
 */
export function NotificationInboxPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { options, label } = useCodes()
  const [tab, setTab] = useState<InboxTab>('UNREAD')
  const { data, isLoading } = useQuery({
    queryKey: ['notifications', 'inbox', tab],
    queryFn: () => fetchInbox(tab === 'ALL' ? undefined : tab),
  })
  const { data: all } = useQuery({ queryKey: ['notifications', 'inbox', 'ALL'], queryFn: () => fetchInbox() })

  /** 탭별 건수를 센다. */
  const countOf = (s: ActionStatus) => all?.content.filter((n) => n.actionStatus === s).length ?? 0

  /** 처리상태를 바꾸고 알림 관련 데이터를 새로 고친다. */
  const changeAction = async (n: NotificationItem, status: ActionStatus, silent = false) => {
    try {
      await changeNotificationAction(n.id, status)
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      if (!silent) message.success(`"${label('actionStatus', status)}"(으)로 변경했습니다.`)
    } catch (e) {
      message.error(toProblem(e).detail)
    }
  }

  /** 업체 상세로 이동한다. 미확인이면 확인 처리한다. */
  const openSupplier = (n: NotificationItem) => {
    if (n.actionStatus === 'UNREAD') changeAction(n, 'READ', true)
    navigate(`/suppliers/${n.supplierId}`)
  }

  /** 미확인 알림을 모두 확인 처리한다. */
  const handleReadAll = async () => {
    const updated = await markAllRead()
    await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    message.success(`${updated}건을 확인 처리했습니다.`)
  }

  return (
    <Card>
      <Flex justify="space-between" align="center" wrap gap={8} style={{ marginBottom: 12 }}>
        <Segmented<InboxTab>
          value={tab}
          onChange={setTab}
          options={[
            { value: 'UNREAD', label: `미확인 ${countOf('UNREAD')}` },
            { value: 'READ', label: `확인 ${countOf('READ')}` },
            { value: 'IN_PROGRESS', label: `처리중 ${countOf('IN_PROGRESS')}` },
            { value: 'DONE', label: `완료 ${countOf('DONE')}` },
            { value: 'ALL', label: `전체 ${all?.totalElements ?? 0}` },
          ]}
        />
        <Flex gap={8} align="center">
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>만료일이 가까운 순 · 갱신이 등록되면 자동으로 완료됩니다.</Typography.Text>
          <Button onClick={handleReadAll} disabled={countOf('UNREAD') === 0}>모두 확인</Button>
        </Flex>
      </Flex>

      {isLoading ? <Skeleton active /> : !data?.content.length ? (
        <Empty description={tab === 'UNREAD' ? '확인하지 않은 알림이 없습니다.' : '알림이 없습니다.'} />
      ) : (
        <Flex vertical gap={6}>
          {data.content.map((n) => (
            <Flex key={n.id} gap={12} align="flex-start" wrap
              style={{ background: n.actionStatus === 'UNREAD' ? '#fffbe6' : '#fff', border: '1px solid #f0f0f0', padding: '12px 14px', borderRadius: 6 }}>
              <DdayBadge daysLeft={n.daysLeft} status={n.daysLeft != null && n.daysLeft < 0 ? 'EXPIRED' : 'IMMINENT'} />
              <div style={{ flex: 1, minWidth: 260 }}>
                <Flex align="center" gap={8} wrap>
                  <Typography.Text strong={n.actionStatus === 'UNREAD'}>{n.title.replace(/^\[[^\]]+\]\s*/, '')}</Typography.Text>
                  <Tag color={ACTION_COLOR[n.actionStatus]}>{label('actionStatus', n.actionStatus)}</Tag>
                </Flex>
                <div style={{ color: '#4b5563', marginTop: 2 }}>{n.message}</div>
                <Flex gap={8} align="center" wrap style={{ marginTop: 6 }}>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {label('targetType', n.targetType)} · 만료 {n.ruleDays}일 전 알림 · {formatDateTime(n.createdAt)}
                  </Typography.Text>
                  {n.deliveries.map((d) => <DeliveryMark key={d.channel} d={d} />)}
                </Flex>
              </div>
              <Flex gap={8} align="center">
                <Button size="small" icon={<ShopOutlined />} onClick={() => openSupplier(n)}>업체 보기</Button>
                <Select<ActionStatus> size="small" style={{ width: 100 }} value={n.actionStatus}
                  options={options('actionStatus')} onChange={(s) => changeAction(n, s)} />
              </Flex>
            </Flex>
          ))}
        </Flex>
      )}
    </Card>
  )
}
