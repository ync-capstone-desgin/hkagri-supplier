/** 로컬 프런트엔드에서 API 응답을 대체해 자료 탭의 오류 복구를 검증한다. DB는 변경하지 않는다. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5174'
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), '로컬 서버에서만 실행하세요.')
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const warning = '계약·인증 목록을 불러오지 못했습니다. 새로고침 후 연결할 항목을 선택해 주세요.'
const missingFile = '파일을 찾을 수 없습니다.'
const fallback = '서버와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요.'
const list = (content = []) => ({ content, page: 0, size: 20, totalElements: content.length })

try {
  for (const role of ['MANAGER', 'VIEWER']) {
    const page = await browser.newPage()
    let failed = true
    let fileRequests = 0
    const targetRequests = { contracts: 0, certifications: 0 }
    let downloadResponse = { status: 404, contentType: 'application/problem+json', body: JSON.stringify({ status: 404, detail: missingFile }) }
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      if (!url.pathname.startsWith('/api/')) return route.continue()
      const path = url.pathname.replace('/api/', '')
      let body = list()
      if (path === 'auth/me') body = { id: 1, username: 'test', name: '검증', role }
      else if (path === 'codes') body = {}
      else if (path === 'cert-types' || path === 'users/options') body = []
      else if (path === 'notifications/unread-count') body = { count: 0 }
      else if (path === 'suppliers/1') body = { id: 1, code: 'TEST', name: '검증 업체', status: 'ACTIVE', contractCount: 0, certCount: 0, expiringCount: 0 }
      else if (path === 'suppliers/1/files') {
        fileRequests++
        body = list([{ file: { id: 1, originalName: '검증.pdf', size: 10, createdAt: '2026-11-01T10:00:00' } }])
      } else if (path === 'files/1/download') return route.fulfill(downloadResponse)
      else if (path in targetRequests && url.searchParams.get('includeInactive') === 'true') {
        targetRequests[path]++
        if (failed) return route.fulfill({ status: 503, json: { status: 503, detail: '목록 조회 실패' } })
        body = list(path === 'contracts'
          ? [{ id: 1, contractNumber: 'C-1', title: '복구된 계약', active: true }]
          : [{ id: 1, certTypeName: '복구된 인증', certificateNumber: 'CERT-1', active: true }])
      }
      await route.fulfill({ json: body })
    })
    await page.goto(`${base}/suppliers/1`)
    await page.getByRole('tab', { name: '자료', exact: true }).click()
    await page.getByText('검증.pdf', { exact: true }).waitFor()
    const reload = page.getByRole('button', { name: '새로고침' })
    if (role === 'MANAGER') {
      await page.getByText(warning, { exact: true }).waitFor()
      await page.waitForFunction(() => !document.querySelector('.ant-btn-loading'))
      const before = { ...targetRequests }
      failed = false
      await reload.click()
      await page.getByText(warning, { exact: true }).waitFor({ state: 'detached' })
      await page.getByLabel('연결할 계약·인증 (선택)').click()
      await page.getByText('계약 · C-1 · 복구된 계약', { exact: true }).waitFor()
      await page.getByText('인증 · 복구된 인증 · CERT-1', { exact: true }).waitFor()
      assert(targetRequests.contracts > before.contracts && targetRequests.certifications > before.certifications)
      await page.keyboard.press('Escape')
    } else {
      const before = fileRequests
      const response = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/suppliers/1/files')
      await reload.click()
      await response
      assert(fileRequests > before)
      assert.deepEqual(targetRequests, { contracts: 0, certifications: 0 })
    }
    await page.getByRole('button', { name: '내려받기' }).click()
    await page.getByText(missingFile, { exact: true }).waitFor()
    // 잘못된 JSON/일반 텍스트/네트워크 실패에서도 기존 안내와 오류 상태를 보존한다.
    for (const body of ['<html>error</html>', '{', 'null']) {
      downloadResponse = { status: 502, contentType: 'text/plain', body }
      const problem = await page.evaluate(async () => {
        const { downloadFile, toProblem } = await import('/src/api/client.ts')
        try { await downloadFile('/files/1/download') } catch (error) { return toProblem(error) }
      })
      assert.deepEqual(problem, { status: 502, detail: fallback })
    }
    await page.route('**/api/files/1/download', (route) => route.abort())
    const problem = await page.evaluate(async () => {
      const { downloadFile, toProblem } = await import('/src/api/client.ts')
      try { await downloadFile('/files/1/download') } catch (error) { return toProblem(error) }
    })
    assert.deepEqual(problem, { status: 0, detail: fallback })
    await page.close()
  }
  console.log('PASS: 연결 목록 실패 후 복구·조회자 새로고침 범위·다운로드 404 안내·비정상 응답/네트워크 오류')
} finally {
  await browser.close()
}
