package com.hankooknongsan.supplier.file.storage;

import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * 첨부파일 내용(바이트)을 실제로 보관하는 저장소.
 * 파일 정보(이름·크기·올린 사람)는 DB에, 내용은 이 저장소에 따로 둔다.
 * 지금은 서버 디스크 구현만 있고, 저장 위치 결정(D-4)에 따라 S3 등 다른 구현을 추가해 설정으로 바꾼다.
 */
public interface FileStorage {

    /**
     * 저장 키 위치에 파일 내용을 저장한다. 같은 키가 이미 있으면 덮어쓰지 않고 실패한다.
     * 쓰는 도중 실패하면 이번에 만든 불완전한 파일은 남기지 않는다.
     */
    void save(String key, InputStream content);

    /** 저장 키 위치의 파일을 지운다. 없으면 아무것도 하지 않는다. (업로드가 취소됐을 때 정리용) */
    void delete(String key);

    /** 저장 키 위치의 파일 내용을 읽을 수 있는 리소스로 돌려준다. 없으면 예외를 던진다. */
    Resource load(String key);
}
