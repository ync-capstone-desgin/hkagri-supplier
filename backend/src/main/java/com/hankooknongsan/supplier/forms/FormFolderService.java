package com.hankooknongsan.supplier.forms;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.forms.FormFolderDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공유 폴더 탐색과 빈 폴더 삭제. 부모 잠금은 중복 생성·삭제 중 추가를 방지한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormFolderService {
    public static final long ROOT_ID = 1;
    private final FormFolderRepository folders;
    private final FormPostRepository posts;
    private final AppClock clock;

    /** 현재 폴더와 상위 경로·바로 아래 폴더를 조회한다. */
    public FolderView get(Long id) {
        var folder = find(id);
        var path = new ArrayList<FolderItem>();
        var current = folder;
        while (current != null) {
            path.add(FolderItem.of(current));
            current = current.getParentId() == null ? null : find(current.getParentId());
        }
        Collections.reverse(path);
        return new FolderView(FolderItem.of(folder), path,
                folders.findByParentIdAndDeletedAtIsNullOrderByNameAscIdAsc(id).stream().map(FolderItem::of).toList());
    }

    /** 폴더가 존재하고 삭제되지 않았는지 확인한다. */
    public FormFolder find(Long id) {
        return folders.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> BusinessException.notFound("폴더"));
    }

    /** 내용을 추가하거나 폴더를 바꾸는 트랜잭션 안에서 행을 잠근다. */
    public FormFolder locked(Long id) {
        return folders.findForUpdate(id).orElseThrow(() -> BusinessException.notFound("폴더"));
    }

    /** 같은 부모 아래 충돌하지 않는 이름으로 폴더를 만든다. */
    @Transactional
    public FolderItem create(CreateRequest request) {
        locked(request.parentId());
        String name = name(request.name());
        checkName(request.parentId(), name, 0L);
        return FolderItem.of(folders.saveAndFlush(new FormFolder(request.parentId(), name)));
    }

    /** 상위 폴더부터 잠가 동시 이름 변경에도 중복이 생기지 않도록 한다. */
    @Transactional
    public FolderItem rename(Long id, RenameRequest request) {
        protectRoot(id);
        var parentId = folders.findParentId(id).orElseThrow(() -> BusinessException.notFound("폴더"));
        locked(parentId);
        var folder = locked(id);
        String name = name(request.name());
        checkName(parentId, name, id);
        folder.rename(name);
        folders.flush();
        return FolderItem.of(folder);
    }

    /** 하위 폴더나 살아 있는 양식이 있으면 삭제하지 않는다. */
    @Transactional
    public void delete(Long id, String actor) {
        protectRoot(id);
        locked(folders.findParentId(id).orElseThrow(() -> BusinessException.notFound("폴더")));
        var folder = locked(id);
        if (folders.existsByParentIdAndDeletedAtIsNull(id) || posts.existsByFolderIdAndDeletedAtIsNull(id)) {
            throw BusinessException.invalid("folderId", "하위 폴더와 양식을 먼저 삭제한 뒤 빈 폴더를 삭제해 주세요.");
        }
        folder.delete(actor, clock.now());
    }

    /** 시스템 최상위 폴더의 삭제와 이름 변경을 막는다. */
    private void protectRoot(Long id) {
        if (id == ROOT_ID) throw BusinessException.invalid("folderId", "최상위 폴더는 변경하거나 삭제할 수 없습니다.");
    }

    /** 공백·유니코드를 정규화하고 경로 구분자와 제어문자를 거절한다. */
    private String name(String input) {
        String name = Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
        if (name.isBlank() || name.length() > 100 || name.equals(".") || name.equals("..")
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\')) {
            throw BusinessException.invalid("name", "폴더 이름은 경로 구분자 없이 1~100자로 입력해 주세요.");
        }
        return name;
    }

    /** 부모 행 잠금 아래에서 중복 이름을 확인한다. */
    private void checkName(Long parentId, String name, Long exceptId) {
        if (folders.nameExists(parentId, name, exceptId)) throw BusinessException.invalid("name", "같은 위치에 같은 이름의 폴더가 있습니다.");
    }
}
