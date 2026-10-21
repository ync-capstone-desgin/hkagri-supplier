package com.hankooknongsan.supplier.supplier;

/**
 * 사업자등록번호 검증 규칙.
 * 사업자번호 10자리 중 마지막 자리는 앞 9자리로 계산하는 검증번호이므로, 오타가 있으면 대부분 여기서 걸러진다.
 * <pre>
 * 가중치  1 3 7 1 3 7 1 3 5 (앞 9자리에 차례로 곱함)
 * 합계    = Σ(자리 × 가중치) + ⌊9번째 자리 × 5 / 10⌋
 * 검증번호 = (10 - 합계 % 10) % 10   → 10번째 자리와 같아야 한다
 * </pre>
 * 이 규칙은 번호의 형식이 맞는지만 확인한다. 실제 등록·휴폐업 여부는 국세청 조회가 필요하다.
 */
public final class BusinessNumber {

    /** 앞 9자리에 곱하는 가중치 */
    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    /** 규칙 모음 클래스이므로 생성을 막는다. */
    private BusinessNumber() {
    }

    /**
     * 사업자번호의 검증번호가 맞는지 확인한다. 하이픈은 있어도 없어도 된다.
     * 숫자가 10자리가 아니면 false.
     */
    public static boolean isValid(String number) {
        if (number == null) {
            return false;
        }
        String digits = number.replace("-", "");
        if (!digits.matches("\\d{10}")) {
            return false;
        }
        return checkDigit(digits.substring(0, 9)) == digits.charAt(9) - '0';
    }

    /**
     * 앞 9자리로 검증번호(10번째 자리)를 계산한다.
     *
     * @param first9 숫자 9자리
     */
    public static int checkDigit(String first9) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (first9.charAt(i) - '0') * WEIGHTS[i];
        }
        sum += (first9.charAt(8) - '0') * 5 / 10;
        return (10 - sum % 10) % 10;
    }

    /**
     * 앞 9자리에 검증번호를 붙여 000-00-00000 형식의 올바른 사업자번호를 만든다. (샘플·테스트 데이터용)
     *
     * @param first9 숫자 9자리
     */
    public static String withCheckDigit(String first9) {
        String d = first9 + checkDigit(first9);
        return d.substring(0, 3) + "-" + d.substring(3, 5) + "-" + d.substring(5);
    }
}
