package uz.footballai.club.dto;

import lombok.Data;

@Data
public class ClubUpdateRequest {
    private String name;
    private String city;
    private Integer foundedYear;
}
