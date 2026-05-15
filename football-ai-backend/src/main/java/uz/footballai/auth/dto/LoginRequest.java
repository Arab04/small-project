package uz.footballai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email kiritilishi shart")
    @Email(message = "Email formati noto'g'ri")
    private String email;

    @NotBlank(message = "Parol kiritilishi shart")
    private String password;
}
