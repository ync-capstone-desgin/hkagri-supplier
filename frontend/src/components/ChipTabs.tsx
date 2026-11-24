import type { ReactNode } from 'react'
import { Button, Flex } from 'antd'

/**
 * 여러 줄로 넘어가도 되는 선택 단추 묶음 (휴대폰용 탭·기간 칩).
 * antd Segmented는 한 줄을 전제로 선택 표시를 옮기는 애니메이션을 그려서, 줄이 바뀌면 표시가 다른 줄까지 번진다.
 * 그래서 좁은 화면에서는 단추를 나란히 두고 선택된 것만 색을 채운다.
 */
export function ChipTabs<V extends string>({ value, options, onChange, size }: {
  value: V
  options: { value: V; label: ReactNode }[]
  onChange: (value: V) => void
  size?: 'small' | 'middle' | 'large'
}) {
  return (
    <Flex wrap gap={6} role="tablist">
      {options.map((option) => (
        <Button key={option.value} role="tab" aria-selected={option.value === value} size={size}
          type={option.value === value ? 'primary' : 'default'} onClick={() => onChange(option.value)}>
          {option.label}
        </Button>
      ))}
    </Flex>
  )
}
