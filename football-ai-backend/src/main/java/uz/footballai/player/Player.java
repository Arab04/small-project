package uz.footballai.player;

import jakarta.persistence.*;
import lombok.*;
import uz.footballai.common.BaseEntity;
import uz.footballai.team.Team;

import java.time.LocalDate;

@Entity
@Table(name = "players")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String fullName;

    private Integer jerseyNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position position;

    private LocalDate birthDate;

    private Integer height; // sm

    private Integer weight; // kg

    private String preferredFoot; // "LEFT", "RIGHT", "BOTH"

    private String photoUrl;

    private String nationality;

    // O'yinchi kuchli/zaif tomonlari (murabbiy kiritadi)
    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
