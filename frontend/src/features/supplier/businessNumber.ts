/** 앞 9자리에 곱하는 가중치 (국세청 사업자등록번호 검증 규칙) */
const WEIGHTS = [1, 3, 7, 1, 3, 7, 1, 3, 5]

/**
 * 사업자번호의 검증번호(마지막 자리)가 맞는지 확인한다. 서버의 BusinessNumber.isValid와 같은 규칙이다.
 * 합계 = Σ(앞 9자리 × 가중치) + ⌊9번째 자리 × 5 / 10⌋, 검증번호 = (10 - 합계 % 10) % 10
 * 숫자가 10자리가 아니면 false.
 */
export function isValidBusinessNumber(value: string): boolean {
  const d = value.replace(/-/g, '')
  if (!/^\d{10}$/.test(d)) return false
  const n = [...d].map(Number)
  const sum = WEIGHTS.reduce((acc, w, i) => acc + n[i] * w, 0) + Math.floor((n[8] * 5) / 10)
  return (10 - (sum % 10)) % 10 === n[9]
}
