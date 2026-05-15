package uz.footballai.club.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubResponse {

    private UUID id;
    private String name;
    private String city;
    private Integer foundedYear;
    private String logoUrl;
    private String subscriptionPlan;
    private int maxTeams;
    private int maxAnalysesPerMonth;
    private int analysesUsedThisMonth;
}
