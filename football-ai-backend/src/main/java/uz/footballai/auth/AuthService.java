package uz.footballai.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.auth.dto.AuthResponse;
import uz.footballai.auth.dto.LoginRequest;
import uz.footballai.auth.dto.RegisterRequest;
import uz.footballai.club.Club;
import uz.footballai.club.ClubRepository;
import uz.footballai.club.SubscriptionPlan;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.user.Role;
import uz.footballai.user.User;
import uz.footballai.user.UserRepository;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Bu email allaqachon ro'yxatdan o'tgan: " + request.getEmail());
        }

        // Klub yaratish
        Club club = Club.builder()
                .name(request.getClubName())
                .city(request.getClubCity())
                .subscriptionPlan(SubscriptionPlan.FREE)
                .build();
        club = clubRepository.save(club);

        // Admin foydalanuvchi yaratish
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(Role.CLUB_ADMIN)
                .club(club)
                .active(true)
                .build();
        user = userRepository.save(user);

        log.info("Yangi klub ro'yxatdan o'tdi: {} ({})", club.getName(), user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmailAndDeletedFalse(request.getEmail())
                .orElseThrow(() -> new BusinessException("Foydalanuvchi topilmadi"));

        if (!user.isActive()) {
            throw new BusinessException("Akkaunt bloklangan. Administratorga murojaat qiling.");
        }

        log.info("Foydalanuvchi tizimga kirdi: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new BusinessException("Foydalanuvchi topilmadi"));

        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new BusinessException("Refresh token yaroqsiz yoki muddati o'tgan");
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        if (user.getClub() != null) {
            claims.put("clubId", user.getClub().getId().toString());
        }

        String accessToken = jwtService.generateAccessToken(claims, user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .clubId(user.getClub() != null ? user.getClub().getId() : null)
                .clubName(user.getClub() != null ? user.getClub().getName() : null)
                .build();
    }
}
