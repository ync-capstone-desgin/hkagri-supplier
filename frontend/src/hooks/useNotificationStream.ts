import { useEffect } from 'react'
import { App } from 'antd'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'

/** 서버가 실시간으로 밀어주는 새 알림 내용 */
interface PushedNotification {
  id: number
  title: string
  message: string
  supplierId: number
}

/** 이 시간(ms) 안에 도착한 알림은 팝업 하나로 묶는다. */
const GROUP_WINDOW_MS = 1000

/**
 * 실시간 알림 연결 훅. 로그인한 동안 서버와 SSE 연결을 하나 유지한다.
 * 새 알림이 오면 오른쪽 위에 팝업을 띄우고 🔔 배지·알림함·대시보드를 새로 고친다.
 * 만료 점검 한 번에 여러 건이 오면 팝업이 쏟아지지 않도록 "새 알림 N건" 하나로 묶는다.
 * 연결이 끊기면 브라우저(EventSource)가 알아서 다시 연결한다.
 */
export function useNotificationStream(enabled: boolean) {
  const { notification } = App.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  useEffect(() => {
    if (!enabled) return
    const source = new EventSource('/api/notifications/stream', { withCredentials: true })
    let buffer: PushedNotification[] = []
    let timer: ReturnType<typeof setTimeout> | undefined

    /** 모아 둔 알림을 팝업으로 보여준다. 한 건이면 내용을, 여러 건이면 건수와 첫 건 제목을 보여준다. */
    const flush = () => {
      const items = buffer
      buffer = []
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      if (items.length === 0) return
      notification.warning({
        key: 'incoming',
        title: items.length === 1 ? items[0].title : `새 만료 알림 ${items.length}건이 도착했습니다`,
        description: items.length === 1 ? items[0].message : `${items[0].title} 외 ${items.length - 1}건 · 눌러서 알림함 열기`,
        placement: 'topRight',
        duration: 8,
        onClick: () => navigate('/notifications'),
        style: { cursor: 'pointer' },
      })
    }

    /** 새 알림 이벤트를 모아 두었다가 잠시 뒤 한 번에 보여준다. */
    const onNotification = (e: MessageEvent<string>) => {
      buffer.push(JSON.parse(e.data) as PushedNotification)
      clearTimeout(timer)
      timer = setTimeout(flush, GROUP_WINDOW_MS)
    }

    source.addEventListener('notification', onNotification as EventListener)
    return () => {
      clearTimeout(timer)
      source.removeEventListener('notification', onNotification as EventListener)
      source.close()
    }
  }, [enabled, notification, queryClient, navigate])
}
