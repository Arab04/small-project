package uz.footballai.match;

import jakarta.persistence.*;
import lombok.*;
import uz.footballai.common.BaseEntity;

@Entity
@Table(name = "match_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Match match;

    @Column(nullable = false)
    private Integer minute;

    private Integer additionalMinute; // qo'shimcha vaqt (90+3)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamSide teamSide; // HOME yoki AWAY

    private String playerName;

    private String secondPlayerName; // almashtirishda kiruvchi o'yinchi

    @Column(columnDefinition = "TEXT")
    private String description;

    // Maydon koordinatalari (0-100 orasida, x va y)
    private Double xCoordinate;
    private Double yCoordinate;
}
