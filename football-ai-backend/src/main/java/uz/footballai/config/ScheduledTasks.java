package uz.footballai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.club.Club;
import uz.footballai.club.ClubRepository;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final ClubRepository clubRepository;

    /**
     * Har oyning 1-kuni soat 00:00 da barcha klublarning
     * oylik tahlil hisoblagichini nolga tushirish.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void resetMonthlyAnalysisCounts() {
        log.info("Oylik tahlil hisoblagichlari nollanmoqda...");

        List<Club> clubs = clubRepository.findAll();
        int count = 0;
        for (Club club : clubs) {
            if (club.getAnalysesUsedThisMonth() > 0) {
                club.resetMonthlyAnalysisCount();
                clubRepository.save(club);
                count++;
            }
        }

        log.info("{} ta klubning oylik tahlil hisoblagichi nollandi", count);
    }
}
