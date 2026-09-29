import { useEffect, useMemo, useState } from 'react'
import { Alert, App, Button, Card, Checkbox, Flex, Select, Space, Switch, Table, Tag, Typography } from 'antd'
import { MailOutlined, PlayCircleOutlined, SaveOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColDef } from 'ag-grid-community'
import { toProblem } from '../../api/client'
import { fetchClock } from '../../api/dashboard'
import { fetchDeliveries, fetchRules, runExpiryScan, updateRule } from '../../api/notifications'
import type { Channel, DeliveryLogItem, NotificationRule } from '../../api/types'
import { useAuth } from '../../app/AuthContext'
import { DataGrid } from '../../components/DataGrid'
import { formatDateTime } from '../../components/format'
import { useCodes } from '../../hooks/useCodes'

/** 알림 기준 한 줄을 화면에서 고치는 동안의 값 */
interface RuleDraft {
  days: number[]
  channels: Channel[]
  enabled: boolean
}

/** 기준일 입력값(문자열 태그)을 0~365 사이의 숫자로 정리한다. 잘못된 값은 버린다. */
function toDays(values: (string | number)[]): number[] {
  return [...new Set(values.map(Number).filter((d) => Number.isInteger(d) && d >= 0 && d <= 365))].sort((a, b) => b - a)
}

/**
 * 알림 설정 화면 (FR-010, FR-011).
 * 1) 알림 기준: 대상별로 만료 며칠 전에, 어떤 채널로 보낼지 정한다 (관리자만 변경).
 * 2) 시연 도구: 매일 08시 점검을 기다리지 않고 지금 실행한다 (dev·관리자).
 * 3) 발송 이력: 채널별 발송 결과·재시도·실패 사유를 확인한다 (관리자).
 */
export function SettingsPage() {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const { me } = useAuth()
  const { label, options } = useCodes()
  const isAdmin = me?.role === 'ADMIN'
  const { data: rules } = useQuery({ queryKey: ['rules'], queryFn: fetchRules })
  const { data: clock } = useQuery({ queryKey: ['clock'], queryFn: fetchClock })
  const { data: deliveries, refetch: refetchDeliveries } = useQuery({
    queryKey: ['deliveries'], queryFn: fetchDeliveries, enabled: isAdmin, refetchInterval: 10_000,
  })
  const [drafts, setDrafts] = useState<Record<number, RuleDraft>>({})
  const [running, setRunning] = useState(false)

  // 서버 값이 바뀌면 편집값을 서버 값으로 맞춘다.
  useEffect(() => {
    if (rules) setDrafts(Object.fromEntries(rules.map((r) => [r.id, { days: r.days, channels: r.channels, enabled: r.enabled }])))
  }, [rules])

  /** 알림 기준 한 줄을 저장한다. */
  const saveRule = async (rule: NotificationRule) => {
    try {
      await updateRule(rule.id, drafts[rule.id])
      await queryClient.invalidateQueries({ queryKey: ['rules'] })
      message.success(`${label('targetType', rule.targetType)} 알림 기준을 저장했습니다.`)
    } catch (e) {
      message.error(toProblem(e).detail)
    }
  }

  /** 만료 점검을 지금 실행하고 결과를 알려준다. 생성된 알림은 담당자 화면에 실시간으로 뜬다. */
  const handleRun = async () => {
    setRunning(true)
    try {
      const { scan, delivered } = await runExpiryScan()
      await Promise.all([queryClient.invalidateQueries({ queryKey: ['notifications'] }), refetchDeliveries()])
      modal.success({
        title: '만료 점검을 실행했습니다',
        content: (
          <div>
            기준일 {scan.today} · 기간 내 대상 {scan.targets}건<br />
            새 알림 <b>{scan.created}건</b> 생성 · 이미 보낸 알림 {scan.skipped}건 건너뜀 · 제외 {scan.excluded}건(거래중지·갱신 마감)<br />
            발송 처리 {delivered}건 (남은 건은 10초 안에 이어서 발송)
          </div>
        ),
      })
    } catch (e) {
      message.error(toProblem(e).detail)
    } finally {
      setRunning(false)
    }
  }

  /** 발송 이력 그리드 컬럼 정의 */
  const columns = useMemo<ColDef<DeliveryLogItem>[]>(() => [
    { field: 'createdAt', headerName: '생성', width: 150, valueFormatter: (p) => formatDateTime(p.value) },
    { field: 'recipientName', headerName: '수신자', width: 100 },
    { field: 'channel', headerName: '채널', width: 90, valueFormatter: (p) => label('channel', p.value) },
    {
      field: 'status', headerName: '상태', width: 110,
      cellRenderer: (p: { data: DeliveryLogItem }) => (
        <Tag color={p.data.status === 'SENT' ? 'success' : p.data.status === 'FAILED' ? 'error' : 'warning'}>
          {label('deliveryStatus', p.data.status)}
        </Tag>
      ),
    },
    { field: 'retryCount', headerName: '실패 횟수', width: 95, cellClass: 'num' },
    { field: 'title', headerName: '알림 제목', flex: 1, minWidth: 220 },
    { field: 'sentAt', headerName: '발송 시각', width: 150, valueFormatter: (p) => formatDateTime(p.value) },
    { field: 'lastError', headerName: '실패 사유', width: 200, tooltipField: 'lastError' },
  ], [label])

  return (
    <Flex vertical gap={12}>
      <Card size="small" title="알림 기준" extra={!isAdmin && <Tag>관리자만 변경할 수 있습니다</Tag>}>
        <Table<NotificationRule>
          rowKey="id"
          size="middle"
          pagination={false}
          dataSource={rules}
          columns={[
            { title: '대상', dataIndex: 'targetType', width: 90, render: (v) => <Tag color={v === 'CONTRACT' ? 'blue' : 'cyan'}>{label('targetType', v)}</Tag> },
            {
              title: '만료 며칠 전에 알릴까요?', key: 'days',
              render: (_, r) => (
                <Select mode="tags" style={{ minWidth: 220 }} disabled={!isAdmin} value={drafts[r.id]?.days.map(String)}
                  tokenSeparators={[',', ' ']} placeholder="예: 90, 30, 7"
                  onChange={(v) => setDrafts({ ...drafts, [r.id]: { ...drafts[r.id], days: toDays(v) } })}
                  tagRender={({ label: l, closable, onClose }) => <Tag closable={closable} onClose={onClose} style={{ marginInlineEnd: 4 }}>{l}일 전</Tag>} />
              ),
            },
            {
              title: '채널', key: 'channels', width: 170,
              render: (_, r) => (
                <Checkbox.Group disabled={!isAdmin} value={drafts[r.id]?.channels} options={options('channel')}
                  onChange={(v) => setDrafts({ ...drafts, [r.id]: { ...drafts[r.id], channels: v as Channel[] } })} />
              ),
            },
            {
              title: '사용', key: 'enabled', width: 80,
              render: (_, r) => (
                <Switch disabled={!isAdmin} checked={drafts[r.id]?.enabled}
                  onChange={(v) => setDrafts({ ...drafts, [r.id]: { ...drafts[r.id], enabled: v } })} />
              ),
            },
            { title: '최종 수정', key: 'updated', width: 170, render: (_, r) => <Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.updatedBy ?? '-'} · {formatDateTime(r.updatedAt)}</Typography.Text> },
            ...(isAdmin ? [{
              title: '', key: 'save', width: 90,
              render: (_: unknown, r: NotificationRule) => <Button type="primary" size="small" icon={<SaveOutlined />} onClick={() => saveRule(r)}>저장</Button>,
            }] : []),
          ]}
        />
        <Alert style={{ marginTop: 12 }} type="info" showIcon
          title="남은 일수에 해당하는 가장 작은 기준 하나로 알립니다. 예: 기준이 90·30·7일이고 만료가 20일 남았다면 '30일 전' 알림 1건. 같은 알림은 두 번 보내지 않습니다." />
      </Card>

      {isAdmin && clock?.demoClock && (
        <Card size="small" title="시연 도구">
          <Space wrap>
            <Button type="primary" icon={<PlayCircleOutlined />} loading={running} onClick={handleRun}>만료 점검 지금 실행</Button>
            <Button icon={<MailOutlined />} href="http://localhost:8025" target="_blank">개발용 메일함 열기 (Mailpit)</Button>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              평소에는 매일 08:00에 자동 실행됩니다. 상단 기준일을 옮긴 뒤 실행하면 미래 상황을 재현할 수 있습니다.
            </Typography.Text>
          </Space>
        </Card>
      )}

      {isAdmin && (
        <Card size="small" title="발송 이력 (최근 200건)" extra={<Typography.Text type="secondary" style={{ fontSize: 12 }}>10초마다 자동 새로고침</Typography.Text>}>
          <DataGrid rows={deliveries} columns={columns} height={420} />
        </Card>
      )}
    </Flex>
  )
}
