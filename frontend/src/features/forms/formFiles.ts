/** 백엔드의 기본 파일 정책과 같은 허용 형식. */
export const formFileAccept = '.pdf,.jpg,.jpeg,.png,.gif,.hwp,.hwpx,.doc,.docx,.xls,.xlsx,.ppt,.pptx'
const maxBytes = 20 * 1024 * 1024

/** 새 파일의 크기·형식·합계를 검사한다. 요청 전체 21MB 중 1MB를 본문·헤더 여유로 둔다. */
export function formUploadError(files: File[]): string | undefined {
  for (const file of files) {
    const ext = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
    if (!formFileAccept.split(',').includes(ext)) return `${file.name}: PDF·이미지·한글·오피스 문서만 올릴 수 있습니다.`
    if (file.size === 0) return `${file.name}: 빈 파일은 올릴 수 없습니다.`
    if (file.size > maxBytes) return `${file.name}: 파일 하나는 20MB 이하여야 합니다.`
  }
  if (files.reduce((sum, file) => sum + file.size, 0) > maxBytes) return '한 번에 새로 올리는 파일의 합계는 20MB 이하여야 합니다.'
}

/** 파일 크기를 읽기 쉬운 단위로 표시한다. */
export function formFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
