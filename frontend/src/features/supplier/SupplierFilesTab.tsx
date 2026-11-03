import { useState } from 'react'
import { Alert, App, Button, Flex, Select, Table, Typography, Upload } from 'antd'
import { DeleteOutlined, DownloadOutlined, InboxOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { searchContracts } from '../../api/contracts'
import { searchCertifications } from '../../api/certifications'
import { deleteStoredFile, downloadStoredFile, fetchSupplierFiles, uploadSupplierFile, type SupplierFile } from '../../api/files'
import { toProblem } from '../../api/client'
import { useAuth } from '../../app/AuthContext'
import { formatDateTime } from '../../components/format'

/** 파일 크기를 화면에서 읽기 쉬운 단위로 표시한다. */
function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** 업체 자료 목록과 다중 업로드. 조회자는 목록·다운로드만 이용한다. */
export function SupplierFilesTab({ supplierId }: { supplierId: number }) {
  const { canEdit } = useAuth()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [target, setTarget] = useState<string>()
  const files = useQuery({ queryKey: ['supplier-files', supplierId, page], queryFn: () => fetchSupplierFiles(supplierId, page) })
  const contracts = useQuery({ queryKey: ['contracts', { supplierId, includeInactive: true }],
    queryFn: () => searchContracts({ supplierId, includeInactive: true }), enabled: canEdit })
  const certs = useQuery({ queryKey: ['certifications', { supplierId, includeInactive: true }],
    queryFn: () => searchCertifications({ supplierId, includeInactive: true }), enabled: canEdit })
  const targetOptions = [
    ...(contracts.data?.content ?? []).map((c) => ({ value: `contract:${c.id}`, label: `계약 · ${c.contractNumber} · ${c.title}${c.active ? '' : ' (이전 이력)'}` })),
    ...(certs.data?.content ?? []).map((c) => ({ value: `certification:${c.id}`, label: `인증 · ${c.certTypeName} · ${c.certificateNumber}${c.active ? '' : ' (이전 이력)'}` })),
  ]

  /** 변경 후 첫 페이지로 돌아가 모든 업체 자료 페이지를 새로 조회한다. */
  const refresh = async () => {
    setPage(0)
    await queryClient.invalidateQueries({ queryKey: ['supplier-files', supplierId] })
  }

  /** 수동 새로고침에서는 연결 대상 조회 실패도 재시도한다. 조회자는 자료만 조회한다. */
  const reload = async () => {
    await Promise.all([files.refetch(), ...(canEdit ? [contracts.refetch(), certs.refetch()] : [])])
  }

  /** 파일명과 삭제 범위를 확인받은 뒤 삭제한다. 실패하면 확인 창을 유지한다. */
  const confirmDelete = (row: SupplierFile) => {
    modal.confirm({ title: '자료를 삭제하시겠습니까?', content: `${row.file.originalName} — 연결된 계약·인증에서도 내려받을 수 없게 됩니다.`,
      okText: '삭제', cancelText: '취소', okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteStoredFile(row.file.id)
          message.success('자료를 삭제했습니다.')
          await refresh()
        } catch (error) {
          message.error(toProblem(error).detail)
          throw error
        }
      },
    })
  }

  /** 내려받기 오류는 화면에 안내한다. */
  const download = async (id: number) => {
    try { await downloadStoredFile(id) } catch (error) { message.error(toProblem(error).detail) }
  }

  return (
    <Flex vertical gap={16}>
      {canEdit && <>
        <Flex vertical gap={8}>
          <label htmlFor="file-target">연결할 계약·인증 (선택)</label>
          <Select id="file-target" allowClear showSearch={{ optionFilterProp: 'label' }} value={target} onChange={setTarget}
            placeholder="선택하지 않으면 업체 일반 자료로 보관합니다" options={targetOptions}
            loading={contracts.isLoading || certs.isLoading} style={{ width: '100%' }} />
          {(contracts.isError || certs.isError) && <Alert type="warning" showIcon title="계약·인증 목록을 불러오지 못했습니다. 새로고침 후 연결할 항목을 선택해 주세요." />}
        </Flex>
        <Upload.Dragger multiple accept=".pdf,.jpg,.jpeg,.png,.gif,.hwp,.hwpx,.doc,.docx,.xls,.xlsx,.ppt,.pptx"
          customRequest={async ({ file, onSuccess, onError }) => {
            try {
              const saved = await uploadSupplierFile(supplierId, file as File, target)
              onSuccess?.(saved)
              await refresh()
            } catch (error) {
              const detail = toProblem(error).detail
              onError?.(new Error(detail))
              message.error(`${(file as File).name}: ${detail}`)
            }
          }} showUploadList={{ showRemoveIcon: false }}>
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">파일을 끌어다 놓거나 클릭해서 올리세요</p>
          <p className="ant-upload-hint">여러 파일을 한 번에 선택할 수 있습니다. PDF·이미지·한글·오피스 문서, 기본 파일당 20MB</p>
        </Upload.Dragger>
      </>}
      <Flex justify="space-between" align="center">
        <Typography.Text>자료 {files.data?.totalElements ?? 0}개</Typography.Text>
        <Button icon={<ReloadOutlined />} onClick={() => void reload()}
          loading={files.isFetching || (canEdit && (contracts.isFetching || certs.isFetching))}>새로고침</Button>
      </Flex>
      {files.isError && <Alert type="error" showIcon title={toProblem(files.error).detail} />}
      <Table<SupplierFile> rowKey={(row) => row.file.id} dataSource={files.data?.content} loading={files.isLoading} size="small"
        scroll={{ x: 900 }} locale={{ emptyText: '등록된 자료가 없습니다.' }}
        pagination={{ current: page + 1, pageSize: 20, total: files.data?.totalElements ?? 0, showSizeChanger: false, onChange: (next) => setPage(next - 1) }}
        columns={[
          { title: '파일 이름', dataIndex: ['file', 'originalName'], width: 250, render: (name) => <span style={{ overflowWrap: 'anywhere' }}>{name}</span> },
          { title: '크기', dataIndex: ['file', 'size'], width: 90, render: fileSize },
          { title: '올린 사람', dataIndex: ['file', 'createdBy'], width: 110, render: (by) => by ?? '-' },
          { title: '올린 날짜', dataIndex: ['file', 'createdAt'], width: 160, render: formatDateTime },
          { title: '연결된 계약·인증', render: (_, row) => row.contractId ? `계약 · ${row.contractName}` : row.certificationId ? `인증 · ${row.certificationName}` : '업체 일반 자료' },
          { title: '작업', width: canEdit ? 170 : 100, render: (_, row) => <Flex gap={4}>
            <Button size="small" icon={<DownloadOutlined />} onClick={() => void download(row.file.id)}>내려받기</Button>
            {canEdit && <Button size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDelete(row)}>삭제</Button>}
          </Flex> },
        ]} />
    </Flex>
  )
}
