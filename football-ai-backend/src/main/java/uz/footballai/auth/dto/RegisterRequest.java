package uz.footballai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Ism kiritilishi shart")
    private String fullName;

    @NotBlank(message = "Email kiritilishi shart")
    @Email(message = "Email formati noto'g'ri")
    private String email;

    @NotBlank(message = "Parol kiritilishi shart")
    @Size(min = 6, message = "Parol kamida 6 ta belgidan iborat bo'lishi kerak")
    private String password;

    private String phoneNumber;

    @NotBlank(message = "Klub nomi kiritilishi shart")
    private String clubName;

    private String clubCity;
}
