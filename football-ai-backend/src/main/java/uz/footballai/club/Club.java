package uz.footballai.club;

import jakarta.persistence.*;
import lombok.*;
import uz.footballai.common.BaseEntity;
import uz.footballai.team.Team;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clubs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Club extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String city;

    private Integer foundedYear;

    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    private LocalDateTime subscriptionExpiresAt;

    @Column(name = "max_teams")
    @Builder.Default
    private int maxTeams = 1;

    @Column(name = "max_analyses_per_month")
    @Builder.Default
    private int maxAnalysesPerMonth = 3;

    @Column(name = "analyses_used_this_month")
    @Builder.Default
    private int analysesUsedThisMonth = 0;

    @OneToMany(mappedBy = "club", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Team> teams = new ArrayList<>();

    public boolean canCreateTeam() {
        long activeTeams = teams.stream().filter(t -> !t.isDeleted()).count();
        return activeTeams < maxTeams;
    }

    public boolean canRequestAnalysis() {
        return analysesUsedThisMonth < maxAnalysesPerMonth;
    }

    public void incrementAnalysisCount() {
        this.analysesUsedThisMonth++;
    }

    public void resetMonthlyAnalysisCount() {
        this.analysesUsedThisMonth = 0;
    }
}
