package com.hankooknongsan.supplier.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 목록 화면의 [엑셀 다운로드] 기능을 위한 xlsx 생성기.
 * 컬럼 정의(헤더명 + 값 추출 함수)와 데이터 목록을 넘기면
 * 회색 굵은 헤더, 첫 행 고정이 적용된 엑셀 파일 응답을 만든다.
 */
public final class ExcelExporter {

    /** 엑셀 컬럼 하나: 헤더 이름과, 행 데이터에서 셀 값을 꺼내는 함수 */
    public record Column<T>(String header, Function<T, Object> value) {
    }

    /** 유틸리티 클래스이므로 생성을 막는다. */
    private ExcelExporter() {
    }

    /**
     * 데이터 목록을 엑셀 파일로 만들어 다운로드 응답으로 반환한다.
     * 숫자는 숫자 셀로, 나머지는 문자열 셀로 쓰고, 한글 파일명은 UTF-8로 인코딩한다.
     *
     * @param fileName 시트 이름이자 다운로드 파일명(확장자 제외)
     * @param columns  컬럼 정의 목록
     * @param rows     엑셀에 쓸 데이터 목록
     */
    public static <T> ResponseEntity<byte[]> export(String fileName, List<Column<T>> columns, List<T> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(fileName);
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i).header());
                header.getCell(i).setCellStyle(headerStyle);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < columns.size(); c++) {
                    Object v = columns.get(c).value().apply(rows.get(r));
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(v == null ? "" : v.toString());
                    }
                }
            }
            for (int i = 0; i < columns.size(); i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);

            String encoded = URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
