/**
 * 로컬 테스트 DB를 사용하는 실제 API·화면 가입 검증.
 * 백엔드(8080)·프론트엔드(5173)를 실행한 뒤 node scripts/check-signup.mjs로 실행한다.
 * 실행마다 가상 승인 대기 계정 1개를 만든다. 운영 서버에서는 실행하지 않는다.
 */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5173'
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), '로컬 서버에서만 실행하세요.')
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1280, height: 1000 }, locale: 'ko-KR' })
const page = await context.newPage()
const username = `check_${Date.now()}`
const password = 'local-check-123!'
const out = new URL('../../docs/images/signup/', import.meta.url)
mkdirSync(out, { recursive: true })

/** 이름·아이디·메일·비밀번호 확인을 화면에 입력한다. */
async function fillSignup() {
  await page.getByLabel('이름', { exact: true }).fill('가입 검증')
  await page.getByLabel('아이디', { exact: true }).fill(username)
  await page.getByLabel('메일', { exact: true }).fill(`${username}@example.com`)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  await page.getByLabel('비밀번호 확인', { exact: true }).fill(password)
  await page.getByText('비밀번호가 일치하지 않습니다.', { exact: true }).waitFor({ state: 'hidden' })
  await page.getByLabel('가입 사유 (선택)', { exact: true }).fill('로컬 기능 검증용 가상 신청입니다.')
}

try {
  await page.goto(`${base}/login`)
  await page.getByRole('link', { name: '회원가입', exact: true }).click()
  await page.locator('button[type=submit]').click()
  await page.getByText('이름을 입력해 주세요.', { exact: true }).waitFor()
  await fillSignup()
  await page.getByLabel('비밀번호 확인', { exact: true }).fill('different')
  await page.locator('button[type=submit]').click()
  await page.getByText('비밀번호가 일치하지 않습니다.', { exact: true }).waitFor()
  await page.getByLabel('비밀번호 확인', { exact: true }).fill(password)
  await page.getByText('비밀번호가 일치하지 않습니다.', { exact: true }).waitFor({ state: 'hidden' })
  await page.screenshot({ path: fileURLToPath(new URL('form.png', out)), fullPage: true })
  await page.locator('button[type=submit]').click()
  await page.getByText('가입 신청이 완료되었습니다.', { exact: true }).waitFor()
  await page.screenshot({ path: fileURLToPath(new URL('success.png', out)), fullPage: true })
  assert.equal((await context.request.get(`${base}/api/auth/me`)).status(), 401)
  await page.getByRole('link', { name: '로그인으로 돌아가기' }).click()
  await page.getByLabel('아이디', { exact: true }).fill(username)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.getByText('승인 대기 중입니다. 관리자 승인 후 로그인해 주세요.', { exact: true }).waitFor()
  await page.screenshot({ path: fileURLToPath(new URL('pending-login.png', out)), fullPage: true })
  await page.getByRole('link', { name: '회원가입', exact: true }).click()
  await fillSignup()
  await page.locator('button[type=submit]').click()
  await page.getByText('이미 사용 중인 아이디입니다.', { exact: true }).waitFor()
  await page.getByLabel('아이디', { exact: true }).fill(`${username}_2`)
  await page.locator('button[type=submit]').click()
  await page.getByText('이미 사용 중인 메일입니다.', { exact: true }).waitFor()
  await page.setViewportSize({ width: 360, height: 800 })
  await page.waitForFunction(() => document.documentElement.scrollWidth <= window.innerWidth)
  await page.screenshot({ path: fileURLToPath(new URL('mobile.png', out)), fullPage: true })
  console.log('PASS: 입력 검증, 가입, 세션 미발급, 승인 대기 로그인, 중복 아이디·메일, 360px 화면')
} catch (error) {
  console.error(await page.locator('body').innerText())
  throw error
} finally {
  await browser.close()
}
