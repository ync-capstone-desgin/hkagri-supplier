import type { ReactNode } from 'react'
import { Button, Card, Flex } from 'antd'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons'

/**
 * 목록 화면 상단의 검색조건 영역.
 * 입력칸은 children으로 받고, [초기화]·[조회] 버튼을 오른쪽에 둔다.
 * 입력칸에서 Enter를 누르면 조회된다 (form submit).
 */
export function SearchPanel({ children, onSearch, onReset }: {
  children: ReactNode
  onSearch: () => void
  onReset: () => void
}) {
  return (
    <Card size="small" style={{ marginBottom: 12 }}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          onSearch()
        }}
      >
        <Flex wrap gap={12} align="center">
          {children}
          <div style={{ flex: 1 }} />
          <Button icon={<ReloadOutlined />} onClick={onReset}>
            초기화
          </Button>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
            조회
          </Button>
        </Flex>
      </form>
    </Card>
  )
}

/** 검색조건 한 칸: 왼쪽에 이름, 오른쪽에 입력 요소 */
export function SearchField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Flex align="center" gap={8}>
      <span style={{ color: '#4b5563', whiteSpace: 'nowrap' }}>{label}</span>
      {children}
    </Flex>
  )
}
