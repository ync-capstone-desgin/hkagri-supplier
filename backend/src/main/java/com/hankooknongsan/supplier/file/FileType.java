package com.hankooknongsan.supplier.file;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 올릴 수 있는 파일 형식 (NFR-006: PDF·이미지·오피스 문서, 한글 문서 포함).
 * <p>
 * 확장자로 형식을 정하고, 파일 앞부분의 서명(매직 바이트)이 그 형식과 맞는지 함께 확인한다.
 * 브라우저가 보내는 형식(Content-Type)은 HWP처럼 비어 오거나 틀리는 경우가 많아 쓰지 않고,
 * 저장·내려받기에는 여기서 정한 형식을 쓴다.
 */
public enum FileType {
    PDF("application/pdf", Signature.PDF, "pdf"),
    JPEG("image/jpeg", Signature.JPEG, "jpg", "jpeg"),
    PNG("image/png", Signature.PNG, "png"),
    GIF("image/gif", Signature.GIF, "gif"),
    /** 한글 문서 (HWP 5.0, 오피스 97~2003과 같은 OLE 구조) */
    HWP("application/x-hwp", Signature.OLE, "hwp"),
    /** 한글 문서 (HWPX, 압축(zip) 구조) */
    HWPX("application/hwp+zip", Signature.ZIP, "hwpx"),
    DOC("application/msword", Signature.OLE, "doc"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Signature.ZIP, "docx"),
    XLS("application/vnd.ms-excel", Signature.OLE, "xls"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Signature.ZIP, "xlsx"),
    PPT("application/vnd.ms-powerpoint", Signature.OLE, "ppt"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", Signature.ZIP, "pptx");

    /** 화면 안내에 쓰는 허용 형식 설명 */
    public static final String ALLOWED_DESCRIPTION =
            "PDF, 이미지(JPG·PNG·GIF), 한글(HWP·HWPX), 워드·엑셀·파워포인트 파일만 올릴 수 있습니다.";

    private final String contentType;
    private final Signature signature;
    private final List<String> extensions;

    /** 형식별 MIME 형식, 파일 서명, 확장자를 지정한다. */
    FileType(String contentType, Signature signature, String... extensions) {
        this.contentType = contentType;
        this.signature = signature;
        this.extensions = List.of(extensions);
    }

    /** 저장·내려받기에 쓰는 MIME 형식을 반환한다. */
    public String contentType() {
        return contentType;
    }

    /** 파일 앞부분(head)이 이 형식의 서명으로 시작하는지 확인한다. */
    public boolean matches(byte[] head) {
        return signature.matches(head);
    }

    /** 파일 이름의 확장자(대소문자 무시)로 형식을 찾는다. 허용하지 않는 확장자면 빈 값. */
    public static Optional<FileType> ofFileName(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extensions.contains(ext)).findFirst();
    }

    /** 서명 확인에 필요한 최대 앞부분 길이(바이트) */
    public static int signatureLength() {
        return Arrays.stream(Signature.values()).mapToInt(s -> s.bytes.length).max().orElse(0);
    }

    /** 파일 형식별 앞부분 서명(매직 바이트) */
    private enum Signature {
        PDF("25504446"),
        JPEG("FFD8FF"),
        PNG("89504E470D0A1A0A"),
        GIF("47494638"),
        /** OLE 복합 문서: HWP 5.0, DOC, XLS, PPT */
        OLE("D0CF11E0A1B11AE1"),
        /** ZIP: HWPX, DOCX, XLSX, PPTX */
        ZIP("504B0304");

        private final byte[] bytes;

        /** 16진수 문자열로 서명 바이트를 지정한다. */
        Signature(String hex) {
            this.bytes = HexFormat.of().parseHex(hex);
        }

        /** 파일 앞부분이 이 서명으로 시작하는지 확인한다. */
        boolean matches(byte[] head) {
            return head.length >= bytes.length && Arrays.equals(head, 0, bytes.length, bytes, 0, bytes.length);
        }
    }
}
