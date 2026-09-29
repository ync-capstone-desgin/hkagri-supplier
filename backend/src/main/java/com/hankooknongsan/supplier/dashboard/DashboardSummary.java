package com.hankooknongsan.supplier.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.hankooknongsan.supplier.expiry.ExpiryItem;

/**
 * 대시보드 요약 (FR-009).
 *
 * @param today              집계 기준일
 * @param supplierTotal      전체 협력업체 수
 * @param supplierActive     거래중 협력업체 수
 * @param activeContracts    유효 계약 수
 * @param activeCertifications 유효 인증 수
 * @param expired            이미 만료된 유효 계약·인증 수
 * @param within7            오늘~7일 안에 만료되는 수
 * @param within30           오늘~30일 안에 만료되는 수 (7일 이내 포함)
 * @param within90           오늘~90일 안에 만료되는 수 (30일 이내 포함)
 * @param unknown            만료일이 없어 확인이 필요한 인증 수
 * @param inProgress         조치 대상 중 갱신 진행 중인 수
 * @param notStarted         조치 대상 중 아직 미착수인 수
 * @param mine               조치 대상 중 로그인 사용자 담당 건수
 * @param urgent             가장 급한 항목 최대 10건 (만료 → 임박 순)
 */
public record DashboardSummary(LocalDate today, long supplierTotal, long supplierActive, long activeContracts,
                               long activeCertifications, long expired, long within7, long within30, long within90,
                               long unknown, long inProgress, long notStarted, long mine, List<ExpiryItem> urgent) {
}
