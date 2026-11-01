package com.example.moneybug.auth;

import com.example.moneybug.auth.AuthService.Tokens;
import com.example.moneybug.auth.kakao.KakaoClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record KakaoTokenRequest(@NotBlank String accessToken) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    private static final String STATE_COOKIE = "kakao_state";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService authService;
    private final KakaoClient kakaoClient;

    public AuthController(AuthService authService, KakaoClient kakaoClient) {
        this.authService = authService;
        this.kakaoClient = kakaoClient;
    }

    /** Mobile app: send the access token obtained from the Kakao SDK. */
    @PostMapping("/kakao/token")
    public Tokens kakaoToken(@Valid @RequestBody KakaoTokenRequest request) {
        return authService.loginWithKakaoAccessToken(request.accessToken());
    }

    /** Web: redirect the browser to Kakao. A random state is kept in an HttpOnly cookie to prevent login CSRF. */
    @GetMapping("/kakao/login")
    public ResponseEntity<Void> kakaoLogin(HttpServletResponse response) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Cookie cookie = new Cookie(STATE_COOKIE, state);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth/kakao");
        cookie.setMaxAge(300);
        response.addCookie(cookie);

        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, kakaoClient.authorizeUrl(state))
                .build();
    }

    /** Web: Kakao redirects back here with the authorization code. */
    @GetMapping("/kakao/callback")
    public Tokens kakaoCallback(@RequestParam String code, @RequestParam String state,
                                @CookieValue(name = STATE_COOKIE, required = false) String expectedState,
                                HttpServletResponse response) {
        if (expectedState == null || !expectedState.equals(state)) {
            throw new AuthException("Invalid OAuth state");
        }
        Cookie clear = new Cookie(STATE_COOKIE, "");
        clear.setPath("/api/auth/kakao");
        clear.setMaxAge(0);
        response.addCookie(clear);
        return authService.loginWithKakaoCode(code);
    }

    @PostMapping("/refresh")
    public Tokens refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
