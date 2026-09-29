import { useState } from 'react'
import { App, Button, DatePicker, Flex, Popover, Tag, Typography } from 'antd'
import { CalendarOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { fetchClock, setClock } from '../api/dashboard'
import { toProblem } from '../api/client'
import { useAuth } from './AuthContext'

/**
 * 상단 기준일 표시.
 * 모든 D-day는 이 날짜 기준으로 계산된다. 시연용으로 날짜를 옮긴 상태면 주황색 "시연 기준일"로 눈에 띄게 표시한다.
 * 관리자는 dev 환경에서 기준일을 바꿔 "3개월 뒤" 같은 상황을 재현할 수 있다.
 */
export function ClockIndicator() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { me } = useAuth()
  const { data } = useQuery({ queryKey: ['clock'], queryFn: fetchClock, staleTime: 60_000 })
  const [open, setOpen] = useState(false)
  const [picked, setPicked] = useState<Dayjs | null>(null)
  const canShift = !!data?.demoClock && me?.role === 'ADMIN'

  /** 기준일을 바꾸고(또는 실제 날짜로 되돌리고) 날짜에 따라 달라지는 모든 화면 데이터를 새로 고친다. */
  const apply = async (date: string | null) => {
    try {
      const info = await setClock(date)
      queryClient.setQueryData(['clock'], info)
      await queryClient.invalidateQueries()
      message.success(info.shifted ? `기준일을 ${info.today}로 옮겼습니다.` : '실제 날짜로 되돌렸습니다.')
      setOpen(false)
    } catch (e) {
      message.error(toProblem(e).detail)
    }
  }

  if (!data) return null
  const label = (
    <Tag icon={<CalendarOutlined />} color={data.shifted ? 'orange' : undefined} style={{ cursor: canShift ? 'pointer' : 'default', marginInlineEnd: 0 }}>
      {data.shifted ? '시연 기준일 ' : '기준일 '}<span className="num">{data.today}</span>
    </Tag>
  )
  if (!canShift) return label

  return (
    <Popover
      open={open}
      onOpenChange={(v) => { setOpen(v); setPicked(dayjs(data.today)) }}
      trigger="click"
      title="시연용 기준일 변경"
      content={
        <Flex vertical gap={8} style={{ width: 260 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            모든 D-day·만료 판정이 이 날짜 기준으로 바뀝니다. 서버를 재시작하면 실제 날짜로 돌아갑니다.
          </Typography.Text>
          <DatePicker value={picked} onChange={setPicked} style={{ width: '100%' }} />
          <Flex gap={8} justify="flex-end">
            <Button size="small" onClick={() => apply(null)} disabled={!data.shifted}>실제 날짜로</Button>
            <Button size="small" type="primary" onClick={() => picked && apply(picked.format('YYYY-MM-DD'))}>적용</Button>
          </Flex>
        </Flex>
      }
    >
      {label}
    </Popover>
  )
}
