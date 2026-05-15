package uz.footballai.opponent;

import jakarta.persistence.*;
import lombok.*;
import uz.footballai.club.Club;
import uz.footballai.common.BaseEntity;

@Entity
@Table(name = "opponents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Opponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club; // qaysi klub raqib sifatida saqlagan

    @Column(nullable = false)
    private String name;

    private String league;

    private String city;

    private String typicalFormation; // "4-3-3", "4-4-2"

    private String coachName;

    private String logoUrl;

    @Column(columnDefinition = "TEXT")
    private String strengths; // murabbiy tomonidan kiritilgan

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "TEXT")
    private String playStyle; // "possession", "counter-attack", "pressing"

    @Column(columnDefinition = "TEXT")
    private String keyPlayers; // asosiy o'yinchilar haqida eslatma

    @Column(columnDefinition = "TEXT")
    private String notes;
}
