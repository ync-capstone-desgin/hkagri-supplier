package com.hankooknongsan.supplier.notification.channel;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 화면 알림 연결(SSE) 관리자.
 * 사용자가 화면을 열면 서버와 연결 하나를 유지하고, 새 알림이 생기면 그 연결로 바로 밀어준다(🔔 배지 갱신).
 * 한 사용자가 여러 탭을 열 수 있으므로 사용자별로 연결 목록을 둔다. 서버 한 대 기준이다.
 */
@Slf4j
@Component
public class SseHub {

    /** 연결 유지 시간. 끊기면 브라우저(EventSource)가 자동으로 다시 연결한다. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 사용자의 새 연결을 만들고 등록한다. 연결이 끝나거나 오류가 나면 목록에서 뺀다. */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(emitter, "connected", "ok");
        return emitter;
    }

    /** 사용자의 모든 연결로 이벤트를 보낸다. 연결이 없으면(화면을 닫은 상태) 아무것도 하지 않는다. */
    public void send(Long userId, String event, Object data) {
        emitters.getOrDefault(userId, List.of()).forEach(emitter -> send(emitter, event, data));
    }

    /** 현재 연결된 사용자 수를 반환한다. (확인용) */
    public int connectedUsers() {
        return (int) emitters.values().stream().filter(l -> !l.isEmpty()).count();
    }

    /** 프록시·브라우저가 연결을 끊지 않도록 25초마다 빈 주석을 보낸다. */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emitters.values().forEach(list -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }));
    }

    /** 연결 하나로 이벤트를 보낸다. 끊긴 연결이면 정리한다. */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 전송 실패(연결 끊김): {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }
}
