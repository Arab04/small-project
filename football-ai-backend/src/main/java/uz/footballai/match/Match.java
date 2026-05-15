package uz.footballai.match;

import jakarta.persistence.*;
import lombok.*;
import uz.footballai.club.Club;
import uz.footballai.common.BaseEntity;
import uz.footballai.opponent.Opponent;
import uz.footballai.team.Team;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "our_team_id", nullable = false)
    private Team ourTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_id", nullable = false)
    private Opponent opponent;

    @Column(nullable = false)
    private boolean isHome; // biz uy jamoasimizmi

    @Column(nullable = false)
    private LocalDate matchDate;

    private String venue;

    private String competition; // Liga nomi, kubok va h.k.

    private Integer ourScore;

    private Integer opponentScore;

    private String ourFormation;      // "4-3-3"

    private String opponentFormation; // "4-4-2"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MatchStatus status = MatchStatus.SCHEDULED;

    private String videoUrl; // MinIO link

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MatchEvent> events = new ArrayList<>();

    @OneToOne(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private MatchStats stats;

    // ===== Qulaylik metodlari =====

    public String getResult() {
        if (ourScore == null || opponentScore == null) return "N/A";
        if (ourScore > opponentScore) return "WIN";
        if (ourScore < opponentScore) return "LOSS";
        return "DRAW";
    }

    public String getScoreDisplay() {
        if (ourScore == null || opponentScore == null) return "-:-";
        return ourScore + ":" + opponentScore;
    }
}
