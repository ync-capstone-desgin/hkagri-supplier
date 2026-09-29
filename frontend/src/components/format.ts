import dayjs, { type Dayjs } from 'dayjs'

/** 서버 날짜 문자열(YYYY-MM-DD)을 날짜 선택기용 값으로 바꾼다. 비어 있으면 undefined. */
export function toDayjs(value?: string | null): Dayjs | undefined {
  return value ? dayjs(value) : undefined
}

/** 날짜 선택기 값을 서버 날짜 문자열(YYYY-MM-DD)로 바꾼다. 비어 있으면 null. */
export function fromDayjs(value?: Dayjs | null): string | null {
  return value ? value.format('YYYY-MM-DD') : null
}

/** 일시를 "YYYY-MM-DD HH:mm" 형식으로 보여준다. */
export function formatDateTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}
