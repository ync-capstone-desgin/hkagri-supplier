import { useState } from 'react'
import { Button, Card, Descriptions, Empty, Flex, Result, Skeleton, Tabs, Typography } from 'antd'
import { EditOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import dayjs from 'dayjs'
import { fetchSupplier } from '../../api/suppliers'
import { useAuth } from '../../app/AuthContext'
import { useTabTitle } from '../../app/TabsContext'
import { SupplierStatusTag } from '../../components/SupplierStatusTag'
import { SupplierFormModal } from './SupplierFormModal'

/** 일시를 "YYYY-MM-DD HH:mm" 형식으로 보여준다. */
function formatDateTime(value?: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

/**
 * 협력업체 상세 화면 (FR-003, FR-004).
 * 상단에 업체 요약과 [수정] 버튼, 아래 탭에 기본정보·계약·인증을 둔다.
 * 계약·인증 탭은 다음 단계에서 채우며, 지금은 자리만 보여준다.
 */
export function SupplierDetailPage() {
  const id = Number(useParams().id)
  const { canEdit } = useAuth()
  const [editOpen, setEditOpen] = useState(false)
  const { data, isLoading, isError } = useQuery({ queryKey: ['supplier', id], queryFn: () => fetchSupplier(id) })
  useTabTitle(data?.name)

  if (isLoading) {
    return <Card><Skeleton active /></Card>
  }
  if (isError || !data) {
    return <Result status="404" title="협력업체를 찾을 수 없습니다." subTitle="삭제되었거나 주소가 잘못되었습니다." />
  }

  return (
    <>
      <Card style={{ marginBottom: 12 }}>
        <Flex justify="space-between" align="flex-start" wrap gap={12}>
          <div>
            <Flex align="center" gap={8}>
              <Typography.Title level={4} style={{ margin: 0 }}>
                {data.name}
              </Typography.Title>
              <SupplierStatusTag status={data.status} />
            </Flex>
            <Typography.Text type="secondary" className="num">
              {data.code} · 내부 담당자 {data.ownerName ?? '미지정'}
            </Typography.Text>
          </div>
          {canEdit && (
            <Button icon={<EditOutlined />} onClick={() => setEditOpen(true)}>
              수정
            </Button>
          )}
        </Flex>
      </Card>

      <Card>
        <Tabs
          items={[
            {
              key: 'info',
              label: '기본정보',
              children: (
                <>
                  <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
                    <Descriptions.Item label="업체명">{data.name}</Descriptions.Item>
                    <Descriptions.Item label="업체코드"><span className="num">{data.code}</span></Descriptions.Item>
                    <Descriptions.Item label="사업자번호"><span className="num">{data.businessNumber ?? '-'}</span></Descriptions.Item>
                    <Descriptions.Item label="거래상태"><SupplierStatusTag status={data.status} /></Descriptions.Item>
                    <Descriptions.Item label="업체 담당자">{data.contactName ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label="연락처"><span className="num">{data.contactPhone ?? '-'}</span></Descriptions.Item>
                    <Descriptions.Item label="이메일">{data.contactEmail ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label="내부 담당자">{data.ownerName ?? '미지정'}</Descriptions.Item>
                    <Descriptions.Item label="비고" span="filled">
                      <span style={{ whiteSpace: 'pre-wrap' }}>{data.memo ?? '-'}</span>
                    </Descriptions.Item>
                  </Descriptions>
                  <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0, fontSize: 12 }}>
                    등록 {data.createdBy} · {formatDateTime(data.createdAt)} &nbsp;|&nbsp; 최종 수정 {data.updatedBy} ·{' '}
                    {formatDateTime(data.updatedAt)}
                  </Typography.Paragraph>
                </>
              ),
            },
            {
              key: 'contracts',
              label: '계약',
              children: <Empty description="계약 관리는 다음 단계에서 추가됩니다." />,
            },
            {
              key: 'certifications',
              label: '인증',
              children: <Empty description="인증 관리는 다음 단계에서 추가됩니다." />,
            },
          ]}
        />
      </Card>

      <SupplierFormModal open={editOpen} supplier={data} onClose={() => setEditOpen(false)} />
    </>
  )
}
