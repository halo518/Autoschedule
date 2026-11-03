package com.example.moneybug.auth;

import com.example.moneybug.auth.jwt.JwtProvider;
import com.example.moneybug.dto.UserDto;
import com.example.moneybug.entity.User;
import com.example.moneybug.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    public record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn) {}

    public record LoginResult(UserDto.Response user, Tokens tokens) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final Duration refreshTtl;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       JwtProvider jwtProvider, PasswordEncoder passwordEncoder,
                       @Value("${jwt.refresh-ttl-seconds}") long refreshTtlSeconds) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.refreshTtl = Duration.ofSeconds(refreshTtlSeconds);
    }

    @Transactional
    public LoginResult signup(UserDto.SignupRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }
        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        try {
            User saved = userRepository.saveAndFlush(user);
            return new LoginResult(UserDto.Response.from(saved), issue(saved.getId()));
        } catch (DataIntegrityViolationException e) {
            // 동시 가입으로 existsByEmail 검사를 통과한 경우 (unique 제약)
            throw new EmailAlreadyExistsException();
        }
    }

    @Transactional
    public LoginResult login(UserDto.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new AuthException("이메일 또는 비밀번호가 맞지 않습니다."));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException("이메일 또는 비밀번호가 맞지 않습니다.");
        }
        return new LoginResult(UserDto.Response.from(user), issue(user.getId()));
    }

    /** Rotates the refresh token: the presented one is consumed and a new pair is issued. */
    @Transactional(noRollbackFor = AuthException.class)
    public Tokens refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new AuthException("다시 로그인해 주세요."));
        refreshTokenRepository.delete(stored);
        if (stored.isExpired()) {
            throw new AuthException("다시 로그인해 주세요.");
        }
        return issue(stored.getUserId());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
    }

    private Tokens issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshToken(userId, hash(refreshToken), Instant.now().plus(refreshTtl)));
        return new Tokens(jwtProvider.createAccessToken(userId), refreshToken, "Bearer", jwtProvider.accessTtlSeconds());
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
