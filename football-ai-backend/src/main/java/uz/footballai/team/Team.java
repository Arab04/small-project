package uz.footballai.team;

import jakarta.persistence.*;
import lombok.*;
import uz.footballai.club.Club;
import uz.footballai.common.BaseEntity;
import uz.footballai.player.Player;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teams")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false)
    private String name;

    private String ageCategory;    // "Senior", "U-21", "U-19", "U-17"

    private String league;

    private String typicalFormation;  // "4-3-3", "4-4-2", "3-5-2"

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Player> players = new ArrayList<>();
}
