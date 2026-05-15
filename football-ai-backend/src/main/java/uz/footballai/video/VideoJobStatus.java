package uz.footballai.video;

/**
 * Video tahlil job statuslari.
 * 90-min video uchun yangi statuslar qo'shildi:
 *  - DETECTING_PHASES (1-yarim/tanaffus/2-yarim aniqlanmoqda)
 *  - COMPUTING_TIMELINE (5 daqiqalik snapshot'lar)
 *  - GENERATING_REPORTS (heatmap + period comparison)
 *  - CANCELLED (foydalanuvchi to'xtatdi)
 */
public enum VideoJobStatus {
    PENDING,
    DOWNLOADING,
    EXTRACTING_FRAMES,
    DETECTING_EVENTS,
    ANALYZING_PLAYERS,
    READING_SCOREBOARD,
    DETECTING_PHASES,           // YANGI - match phases
    COMPUTING_TIMELINE,         // YANGI - timeline analytics
    GENERATING_REPORTS,         // YANGI - heatmap + comparison
    COMPLETED,
    FAILED,
    CANCELLED
}
