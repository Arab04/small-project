package uz.footballai.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.footballai.match.Match;
import uz.footballai.match.MatchEvent;
import uz.footballai.match.MatchStats;
import uz.footballai.match.MatchStatsRepository;
import uz.footballai.match.MatchEventRepository;
import uz.footballai.opponent.Opponent;
import uz.footballai.player.Player;
import uz.footballai.player.PlayerRepository;
import uz.footballai.team.Team;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final PlayerRepository playerRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchStatsRepository matchStatsRepository;

    /**
     * System prompt — AI ning roli va javob formati.
     */
    public String buildSystemPrompt() {
        return """
            Sen professional futbol taktik tahlilchisisan. Sening vazifang:
            1. Raqib jamoaning kuchli va zaif tomonlarini aniqlash
            2. Bizning jamoamizning kuchli va zaif tomonlarini baholash
            3. Raqibga qarshi g'alaba qozonish uchun aniq taktik reja tuzish
            
            Sen FAQAT quyidagi JSON formatda javob berishing SHART. Hech qanday qo'shimcha matn yozma, 
            faqat JSON qaytar. Har bir maydonni o'zbek tilida, batafsil va aniq yoz.
            
            JAVOB FORMATI:
            {
              "opponent_strengths": "Raqibning kuchli tomonlari (kamida 3-5 ta punkt, batafsil)",
              "opponent_weaknesses": "Raqibning zaif tomonlari (kamida 3-5 ta punkt, batafsil)",
              "our_strengths": "Bizning kuchli tomonlarimiz (kamida 3-5 ta punkt)",
              "our_weaknesses": "Bizning zaif tomonlarimiz (kamida 2-3 ta punkt)",
              "recommended_formation": "Tavsiya etiladigan formatsiya (masalan: 4-2-3-1)",
              "tactical_plan": "Umumiy taktik reja (batafsil, kamida 200 so'z)",
              "attacking_plan": "Hujum rejasi - qayerdan, qanday hujum qilish kerak (batafsil, kamida 150 so'z)",
              "defending_plan": "Himoya rejasi - qanday himoyalanish kerak (batafsil, kamida 150 so'z)",
              "set_piece_plan": "Standart holatlar rejasi - burchak, jarima, autt (kamida 100 so'z)",
              "first_fifteen_minutes_plan": "Birinchi 15 daqiqa rejasi - qanday boshlash kerak",
              "key_players_to_watch": "Raqibning eng xavfli o'yinchilari va ularga qarshi chora",
              "substitution_strategy": "Almashtirishlar strategiyasi - qachon, kimni almashtirish",
              "press_zones": "Pressing zonalari - qayerda bosim o'tkazish kerak",
              "danger_zones": "Xavfli zonalar - raqib qayerdan hujum qiladi",
              "summary": "Qisqa xulosa (2-3 jumla)"
            }
            
            MUHIM QOIDALAR:
            - Faqat berilgan ma'lumotlarga asoslan
            - Aniq va amaliy maslahatlar ber (umumiy gaplar emas)
            - Raqibning zaif tomonlaridan foydalanish yo'llarini ko'rsat
            - Daqiqa va pozitsiya darajasida aniq ko'rsatmalar ber
            - O'zbek futboli kontekstida maslahat ber
            """;
    }

    /**
     * Foydalanuvchi xabari — barcha ma'lumotlar bilan.
     */
    public String buildUserMessage(Team ourTeam, Opponent opponent,
                                    List<Match> opponentMatches, List<Match> ourMatches) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== BIZNING JAMOA ===\n");
        sb.append(buildOurTeamSection(ourTeam, ourMatches));

        sb.append("\n\n=== RAQIB JAMOA ===\n");
        sb.append(buildOpponentSection(opponent, opponentMatches));

        sb.append("\n\n=== VAZIFA ===\n");
        sb.append(String.format(
                "Yuqoridagi ma'lumotlar asosida %s jamoasiga qarshi %s jamoasi uchun " +
                "batafsil taktik tahlil va g'alaba rejasini tayyorla. " +
                "FAQAT JSON formatda javob ber, boshqa hech narsa yozma.",
                opponent.getName(), ourTeam.getName()));

        return sb.toString();
    }

    private String buildOurTeamSection(Team ourTeam, List<Match> ourMatches) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nomi: ").append(ourTeam.getName()).append("\n");
        if (ourTeam.getTypicalFormation() != null) {
            sb.append("Odatiy formatsiya: ").append(ourTeam.getTypicalFormation()).append("\n");
        }
        if (ourTeam.getLeague() != null) {
            sb.append("Liga: ").append(ourTeam.getLeague()).append("\n");
        }

        // O'yinchilar
        List<Player> players = playerRepository.findByTeamIdAndDeletedFalse(ourTeam.getId());
        if (!players.isEmpty()) {
            sb.append("\nO'yinchilar:\n");
            for (Player p : players) {
                sb.append(String.format("  - #%s %s (%s)",
                        p.getJerseyNumber() != null ? p.getJerseyNumber() : "?",
                        p.getFullName(),
                        p.getPosition().name()));
                if (p.getStrengths() != null) sb.append(" | Kuchli: ").append(p.getStrengths());
                if (p.getWeaknesses() != null) sb.append(" | Zaif: ").append(p.getWeaknesses());
                sb.append("\n");
            }
        }

        // Oxirgi o'yinlar
        if (!ourMatches.isEmpty()) {
            sb.append("\nOxirgi o'yinlar:\n");
            int limit = Math.min(ourMatches.size(), 10);
            for (int i = 0; i < limit; i++) {
                Match m = ourMatches.get(i);
                sb.append(buildMatchSummary(m));
            }
        }

        return sb.toString();
    }

    private String buildOpponentSection(Opponent opponent, List<Match> opponentMatches) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nomi: ").append(opponent.getName()).append("\n");
        if (opponent.getTypicalFormation() != null) {
            sb.append("Odatiy formatsiya: ").append(opponent.getTypicalFormation()).append("\n");
        }
        if (opponent.getCoachName() != null) {
            sb.append("Bosh murabbiy: ").append(opponent.getCoachName()).append("\n");
        }
        if (opponent.getPlayStyle() != null) {
            sb.append("O'yin uslubi: ").append(opponent.getPlayStyle()).append("\n");
        }
        if (opponent.getStrengths() != null) {
            sb.append("Ma'lum kuchli tomonlari: ").append(opponent.getStrengths()).append("\n");
        }
        if (opponent.getWeaknesses() != null) {
            sb.append("Ma'lum zaif tomonlari: ").append(opponent.getWeaknesses()).append("\n");
        }
        if (opponent.getKeyPlayers() != null) {
            sb.append("Asosiy o'yinchilari: ").append(opponent.getKeyPlayers()).append("\n");
        }

        // Raqib bilan o'tgan o'yinlar
        if (!opponentMatches.isEmpty()) {
            sb.append("\nBu raqib bilan o'tgan o'yinlarimiz:\n");
            for (Match m : opponentMatches) {
                sb.append(buildMatchSummary(m));
            }
        }

        return sb.toString();
    }

    private String buildMatchSummary(Match match) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  [%s] %s vs %s — %s",
                match.getMatchDate(),
                match.getOurTeam().getName(),
                match.getOpponent().getName(),
                match.getScoreDisplay()));

        if (match.getOurFormation() != null) {
            sb.append(" (bizning formatsiya: ").append(match.getOurFormation()).append(")");
        }
        if (match.getOpponentFormation() != null) {
            sb.append(" (raqib formatsiya: ").append(match.getOpponentFormation()).append(")");
        }
        sb.append(" — Natija: ").append(match.getResult());
        sb.append("\n");

        // Statistika
        matchStatsRepository.findByMatchId(match.getId()).ifPresent(stats -> {
            sb.append("    Statistika: ");
            if (stats.getOurPossession() != null) {
                sb.append("Egalik: ").append(stats.getOurPossession()).append("%-").append(stats.getOppPossession()).append("% | ");
            }
            if (stats.getOurTotalShots() != null) {
                sb.append("Urishlar: ").append(stats.getOurTotalShots()).append("-").append(stats.getOppTotalShots()).append(" | ");
            }
            if (stats.getOurShotsOnTarget() != null) {
                sb.append("Darvozaga: ").append(stats.getOurShotsOnTarget()).append("-").append(stats.getOppShotsOnTarget()).append(" | ");
            }
            if (stats.getOurCorners() != null) {
                sb.append("Burchaklar: ").append(stats.getOurCorners()).append("-").append(stats.getOppCorners()).append(" | ");
            }
            if (stats.getOurPassAccuracy() != null) {
                sb.append("Pas aniqligi: ").append(stats.getOurPassAccuracy()).append("%-").append(stats.getOppPassAccuracy()).append("%");
            }
            if (stats.getOurXg() != null) {
                sb.append(" | xG: ").append(stats.getOurXg()).append("-").append(stats.getOppXg());
            }
            sb.append("\n");
        });

        // Voqealar
        List<MatchEvent> events = matchEventRepository.findByMatchIdAndDeletedFalseOrderByMinuteAsc(match.getId());
        if (!events.isEmpty()) {
            sb.append("    Voqealar: ");
            sb.append(events.stream()
                    .map(e -> String.format("%d' %s (%s%s)",
                            e.getMinute(),
                            e.getType().name(),
                            e.getTeamSide().name(),
                            e.getPlayerName() != null ? " - " + e.getPlayerName() : ""))
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        return sb.toString();
    }
}
