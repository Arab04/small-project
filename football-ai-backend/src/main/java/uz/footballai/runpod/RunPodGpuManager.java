package uz.footballai.runpod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RunPod GPU pod hayot siklini boshqaradi.
 *
 * Strategiya:
 *   1. Foydalanuvchi tahlilni boshlaganda → ensureRunning() chaqiradi.
 *      Pod uxlagan bo'lsa - ishga tushiradi va /health 200 qaytarguncha kutadi.
 *   2. Tahlil davomida va tugagandan keyin - markActivity() chaqiriladi.
 *   3. Har 5 daqiqada @Scheduled idleCheck() ishlaydi.
 *      Agar pod ishlayapti va oxirgi faollikdan beri 10 daqiqa o'tgan bo'lsa - to'xtatadi.
 *
 * Konfiguratsiya (application.yml):
 *   runpod:
 *     enabled: true               # auto-start/stop yoqilganmi (false bo'lsa hech narsa qilmaydi)
 *     api-key: ${RUNPOD_API_KEY}
 *     pod-id: ${RUNPOD_POD_ID}
 *     idle-timeout-minutes: 10    # idle bo'lsa shu vaqtdan keyin stop
 *     start-timeout-seconds: 180  # boshlash uchun max kutish vaqti
 *     health-check-interval-seconds: 5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunPodGpuManager {

    private final RunPodClient runPodClient;

    @Value("${runpod.enabled:false}")
    private boolean enabled;

    @Value("${runpod.pod-id:}")
    private String podId;

    @Value("${runpod.idle-timeout-minutes:10}")
    private int idleTimeoutMinutes;

    @Value("${runpod.start-timeout-seconds:180}")
    private int startTimeoutSeconds;

    @Value("${runpod.health-check-interval-seconds:5}")
    private int healthCheckIntervalSeconds;

    @Value("${ml-service.url:http://localhost:8001}")
    private String mlServiceUrl;

    /**
     * Oxirgi faollik vaqti — har bir analyze yoki callback'da yangilanadi.
     * Idle check shundan kelib chiqib pod'ni stop qiladi.
     */
    private final AtomicReference<Instant> lastActivity = new AtomicReference<>(null);

    /**
     * Joriy holat - "boshlanyapti / ishlayapti / to'xtagan".
     * Concurrent ensureRunning() chaqiruvlarini sinxronlash uchun.
     */
    private final Object stateLock = new Object();

    /**
     * Pod ishlayotganini ta'minlaydi. Agar uxlagan bo'lsa - ishga tushiradi va kutadi.
     *
     * @throws RuntimeException agar pod start bo'lmasa yoki kutish vaqti tugasa
     */
    public void ensureRunning() {
        if (!enabled) {
            log.debug("RunPod auto-management o'chirilgan — ensureRunning() o'tkazib yuboriladi");
            markActivity();
            return;
        }

        if (podId == null || podId.isBlank()) {
            log.warn("runpod.pod-id sozlanmagan — ensureRunning() ishlamaydi");
            return;
        }

        synchronized (stateLock) {
            // 1. /health orqali test - ishlayotgan bo'lsa, hech narsa qilmaymiz
            if (isMlServiceHealthy()) {
                log.debug("RunPod allaqachon ishlayapti");
                markActivity();
                return;
            }

            // 2. RunPod'dan status olamiz
            log.info("ML service /health javob bermayapti, RunPod statusi tekshirilmoqda...");
            RunPodClient.PodStatus status = runPodClient.getStatus(podId);

            if (status.isRunning()) {
                log.info("RunPod RUNNING holatida, lekin /health hali tayyor emas - kutamiz");
            } else if (status.isStopped()) {
                log.info("RunPod EXITED holatida — start so'rovi yuborilmoqda...");
                if (!runPodClient.start(podId)) {
                    throw new RuntimeException("RunPod start bo'lmadi: " + podId);
                }
            } else {
                log.warn("RunPod kutilmagan holatda: {}", status.desiredStatus());
            }

            // 3. /health 200 qaytarguncha kutamiz (max startTimeoutSeconds)
            waitForHealthy();
            markActivity();
        }
    }

    /**
     * Faollik vaqtini yangilash — har bir tahlil so'rovi yoki callback'da chaqiriladi.
     */
    public void markActivity() {
        lastActivity.set(Instant.now());
        log.debug("RunPod faollik belgilandi: {}", lastActivity.get());
    }

    /**
     * Avtomatik idle check — har 5 daqiqada bir marta.
     * Agar pod ishlayapti va lastActivity'dan beri idleTimeoutMinutes o'tgan bo'lsa - stop.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void idleCheck() {
        if (!enabled || podId == null || podId.isBlank()) {
            return;
        }

        Instant last = lastActivity.get();
        if (last == null) {
            // Hech qanday faollik bo'lmagan — agar pod ishlayotgan bo'lsa, vaqtni hozir belgilab,
            // keyingi tekshiruvda to'xtatamiz
            if (isMlServiceHealthy()) {
                log.info("RunPod ishlayapti, lekin lastActivity null — hozirdan boshlab vaqt tekshiriladi");
                markActivity();
            }
            return;
        }

        long minutesIdle = Duration.between(last, Instant.now()).toMinutes();
        log.debug("RunPod idle check: {} daqiqa idle (chegara: {} daq)", minutesIdle, idleTimeoutMinutes);

        if (minutesIdle >= idleTimeoutMinutes) {
            // /health tekshiramiz - haqiqatan ham hech qanday faol job yo'qligini
            if (!hasActiveJob()) {
                log.info("RunPod {} daqiqa idle bo'ldi — to'xtatilmoqda", minutesIdle);
                if (runPodClient.stop(podId)) {
                    lastActivity.set(null);
                }
            } else {
                log.info("RunPod idle, lekin faol job bor — to'xtatilmaydi");
                markActivity(); // vaqtni yangilash
            }
        }
    }

    /**
     * /health endpoint javob beradimi (200 OK)?
     */
    private boolean isMlServiceHealthy() {
        try {
            WebClient wc = WebClient.builder()
                    .baseUrl(mlServiceUrl)
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            String body = wc.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            return body != null && body.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * /health javobida active_jobs > 0 bormi?
     */
    private boolean hasActiveJob() {
        try {
            WebClient wc = WebClient.builder()
                    .baseUrl(mlServiceUrl)
                    .build();

            String body = wc.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            // {"status": "ok", "active_jobs": 1} — 1 yoki ko'proq bo'lsa, faol
            if (body == null) return false;
            if (body.contains("active_jobs\":0") || body.contains("active_jobs\": 0")) return false;
            return body.contains("active_jobs");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * /health 200 qaytarguncha kutadi.
     */
    private void waitForHealthy() {
        long startTime = System.currentTimeMillis();
        long maxWaitMs = startTimeoutSeconds * 1000L;
        int attempt = 0;

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            attempt++;
            if (isMlServiceHealthy()) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                log.info("✅ RunPod {} sek dan keyin tayyor (urinish #{})", elapsed, attempt);
                return;
            }

            try {
                Thread.sleep(healthCheckIntervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("RunPod kutish to'xtatildi");
            }
        }

        throw new RuntimeException(
                "RunPod " + startTimeoutSeconds + " sek ichida ishga tushmadi (pod-id: " + podId + ")"
        );
    }
}
