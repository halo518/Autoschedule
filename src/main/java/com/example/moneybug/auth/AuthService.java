package com.example.moneybug.auth;

import com.example.moneybug.auth.jwt.JwtProvider;
import com.example.moneybug.auth.kakao.KakaoClient;
import com.example.moneybug.auth.kakao.KakaoClient.KakaoUser;
import com.example.moneybug.entity.User; // [수정] user 패키지 삭제 -> entity 패키지로 변경
import com.example.moneybug.repository.UserRepository; // [수정] user 패키지 삭제 -> repository 패키지로 변경
import org.springframework.beans.factory.annotation.Value;
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

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KakaoClient kakaoClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final Duration refreshTtl;

    public AuthService(KakaoClient kakaoClient, UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository, JwtProvider jwtProvider,
                       @Value("${jwt.refresh-ttl-seconds}") long refreshTtlSeconds) {
        this.kakaoClient = kakaoClient;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTtl = Duration.ofSeconds(refreshTtlSeconds);
    }

    /** Mobile: the app already holds a Kakao access token from the Kakao SDK. */
    @Transactional
    public Tokens loginWithKakaoAccessToken(String kakaoAccessToken) {
        kakaoClient.verifyAppId(kakaoAccessToken);
        return login(kakaoClient.fetchUser(kakaoAccessToken));
    }

    /** Web: the browser came back from Kakao with an authorization code. */
    @Transactional
    public Tokens loginWithKakaoCode(String code) {
        String kakaoAccessToken = kakaoClient.exchangeCode(code);
        return login(kakaoClient.fetchUser(kakaoAccessToken));
    }

    /** Rotates the refresh token: the presented one is consumed and a new pair is issued. */
    @Transactional(noRollbackFor = AuthException.class)
    public Tokens refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new AuthException("Invalid refresh token"));
        refreshTokenRepository.delete(stored);
        if (stored.isExpired()) {
            throw new AuthException("Refresh token expired");
        }
        return issue(stored.getUserId());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
    }

    private Tokens login(KakaoUser kakaoUser) {
        String kakaoId = String.valueOf(kakaoUser.kakaoId());
        // [수정] 신규 가입 시 role/name/contact 는 임시값으로 채우고, 이후 사용자가 직접 입력해 갱신한다.
        // 기존 사용자의 정보는 덮어쓰지 않는다.
        User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> {
                    User created = new User();
                    created.setKakaoId(kakaoId);
                    created.setRole(User.Role.WORKER);
                    created.setName(tempName(kakaoUser.nickname()));
                    created.setContact("");
                    return created;
                });
        return issue(userRepository.save(user).getId().longValue());
    }

    // [수정] name 은 NOT NULL, VARCHAR(50) 이므로 닉네임이 없거나 길면 보정한다.
    private static String tempName(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "";
        }
        return nickname.length() > 50 ? nickname.substring(0, 50) : nickname;
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
