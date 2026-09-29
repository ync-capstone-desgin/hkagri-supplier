import { Tag, Tooltip } from 'antd'
import type { ExpiryStatus } from '../api/types'

/** 만료 긴급도 색. 만료(진빨강) · 7일 이하(빨강) · 30일 이하(주황) · 90일 이하(노랑) · 그 외(회색) */
export const URGENCY_COLOR = {
  expired: '#a8071a',
  d7: '#cf1322',
  d30: '#d46b08',
  d90: '#d4b106',
  unknown: '#722ed1',
} as const

/**
 * 남은 일수로 긴급도 단계를 정한다.
 * 색만으로 구분하지 않도록 배지에는 항상 글자(D-3, 만료 등)를 함께 쓴다.
 */
function urgencyOf(daysLeft: number | null, status: ExpiryStatus) {
  if (status === 'UNKNOWN') return { color: URGENCY_COLOR.unknown, solid: false }
  if (daysLeft == null) return { color: undefined, solid: false }
  if (daysLeft < 0) return { color: URGENCY_COLOR.expired, solid: true }
  if (daysLeft <= 7) return { color: URGENCY_COLOR.d7, solid: true }
  if (daysLeft <= 30) return { color: URGENCY_COLOR.d30, solid: false }
  if (daysLeft <= 90) return { color: URGENCY_COLOR.d90, solid: false }
  return { color: undefined, solid: false }
}

/** 남은 일수를 D-day 글자로 바꾼다. 예: D-7, D-DAY, 만료 3일 경과 */
export function ddayText(daysLeft: number | null, status: ExpiryStatus, noExpiryText = '무기한'): string {
  if (status === 'UNKNOWN') return '확인 필요'
  if (daysLeft == null) return noExpiryText
  if (daysLeft > 0) return `D-${daysLeft}`
  if (daysLeft === 0) return 'D-DAY'
  return `만료 ${-daysLeft}일 경과`
}

/**
 * 만료 D-day 배지 (FR-007).
 * 만료·7일 이하는 채운 배지로, 30·90일 이하는 테두리 배지로 보여 급한 정도가 한눈에 보이게 한다.
 */
export function DdayBadge({ daysLeft, status, noExpiryText }: {
  daysLeft: number | null
  status: ExpiryStatus
  /** 만료일이 없을 때 보여줄 글자 (계약: 무기한) */
  noExpiryText?: string
}) {
  const { color, solid } = urgencyOf(daysLeft, status)
  const text = ddayText(daysLeft, status, noExpiryText)
  const tag = (
    <Tag
      className="num"
      variant={solid ? 'solid' : 'outlined'}
      color={color}
      style={{ fontWeight: color ? 600 : 400, ...(solid || !color ? {} : { background: '#fff' }) }}
    >
      {text}
    </Tag>
  )
  return status === 'UNKNOWN' ? <Tooltip title="만료일이 등록되지 않았습니다. 인증서를 확인해 주세요.">{tag}</Tooltip> : tag
}
