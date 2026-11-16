/** 로컬 가상 계정으로 승인·거절·역할 변경·비활성화와 메뉴 접근을 검증하고 캡처한다. */
import assert from 'node:assert/strict'
import { mkdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5174'
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), '로컬 서버에서만 실행하세요.')
const seedPassword = process.env.SEED_PASSWORD ?? readFileSync(new URL('../../.env.example', import.meta.url), 'utf8').match(/^SEED_PASSWORD=(.*)$/m)[1].trim()
const password = 'local-check-123!'
const username = `users_${Date.now()}`
const rejected = `${username}_no`
const out = new URL('../../docs/images/user-management/', import.meta.url)
mkdirSync(out, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const admin = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR' })
const member = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR' })
const page = await admin.newPage()

/** 브라우저 쿠키를 유지한 채 API 요청을 보낸다. */
async function post(context, path, data) {
  return context.request.post(`${base}/api${path}`, { data })
}

/** 표에서 계정 한 행만 찾는다. */
function row(name) {
  return page.locator('tr.ant-table-row').filter({ hasText: `${name}@example.com` })
}

/** 역할 선택 창에서 원하는 역할을 적용한다. */
async function chooseRole(name, action, role) {
  await row(name).getByRole('button', { name: action, exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox').click()
  await page.locator('.ant-select-item-option-content').filter({ hasText: new RegExp(`^${role}$`) }).click()
  await dialog.getByRole('button', { name: action, exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
}

/** 확인 창을 거치는 상태 변경을 실행한다. */
async function confirmAction(name, action) {
  await row(name).getByRole('button', { name: action, exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: action, exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
}

/** 현재 관리 화면을 문서에 사용할 이미지로 저장한다. */
async function capture(name) {
  await page.waitForFunction(() => document.querySelectorAll('.ant-message-notice').length === 0)
  await page.screenshot({ path: fileURLToPath(new URL(name, out)), fullPage: true })
}

try {
  for (const name of [username, rejected]) {
    const response = await post(member, '/auth/signup', { name: '사용자 관리 검증', username: name,
      email: `${name}@example.com`, password, passwordConfirm: password, signupReason: '검증용 가상 계정입니다.' })
    assert.equal(response.status(), 201)
  }
  await page.goto(`${base}/login`)
  await page.getByLabel('아이디', { exact: true }).fill('admin')
  await page.getByLabel('비밀번호', { exact: true }).fill(seedPassword)
  await page.locator('button[type=submit]').click()
  await page.waitForURL('**/dashboard')
  await page.getByRole('menuitem', { name: /사용자 관리/ }).click()
  await row(username).waitFor()
  await capture('pending.png')
  await chooseRole(username, '승인', '담당자')
  assert.equal((await post(member, '/auth/login', { username, password })).status(), 200)
  await confirmAction(rejected, '거절')
  assert.equal((await post(await browser.newContext(), '/auth/login', { username: rejected, password })).status(), 401)
  await page.getByRole('tab', { name: '전체 사용자', exact: true }).click()
  await chooseRole(username, '역할 변경', '조회자')
  const me = await (await member.request.get(`${base}/api/auth/me`)).json()
  assert.equal(me.role, 'VIEWER')
  assert.equal((await member.request.get(`${base}/api/admin/users`)).status(), 403)
  assert.equal((await post(member, '/suppliers', { name: '차단 확인', status: 'ACTIVE' })).status(), 403)
  const memberPage = await member.newPage()
  await memberPage.goto(`${base}/users`)
  await memberPage.getByText('관리자만 사용자 관리에 접근할 수 있습니다.', { exact: true }).waitFor()
  assert.equal(await memberPage.getByRole('menuitem', { name: /사용자 관리/ }).count(), 0)
  await confirmAction(username, '비활성화')
  await row(username).getByRole('button', { name: '다시 사용', exact: true }).waitFor()
  await capture('inactive.png')
  assert.equal((await member.request.get(`${base}/api/auth/me`)).status(), 401)
  assert.equal((await post(member, '/auth/login', { username, password })).status(), 401)
  await confirmAction(username, '다시 사용')
  assert.equal((await post(member, '/auth/login', { username, password })).status(), 200)
  await row(username).getByRole('button', { name: '역할 변경', exact: true }).waitFor()
  await capture('users.png')
  console.log('PASS: 승인·거절·역할 선택, 기존 세션 권한 반영, 비관리자 메뉴/API 차단, 비활성화·재사용')
} catch (error) {
  console.error(await page.locator('body').innerText())
  throw error
} finally {
  await browser.close()
}
