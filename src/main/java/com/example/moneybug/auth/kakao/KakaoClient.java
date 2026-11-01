package com.example.moneybug.auth.kakao;

import com.example.moneybug.auth.AuthException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoClient {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_ME_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";

    public record KakaoUser(Long kakaoId, String nickname, String email, String profileImage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenInfo(@JsonProperty("id") Long id, @JsonProperty("app_id") String appId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserMe(Long id, @JsonProperty("kakao_account") Account kakaoAccount) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Account(String email, Profile profile) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Profile(String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {}
    }

    private final RestClient restClient;
    private final String restApiKey;
    private final String clientSecret;
    private final String redirectUri;
    private final String appId;

    public KakaoClient(@Value("${kakao.rest-api-key}") String restApiKey,
                       @Value("${kakao.client-secret}") String clientSecret,
                       @Value("${kakao.redirect-uri}") String redirectUri,
                       @Value("${kakao.app-id}") String appId) {
        this.restClient = RestClient.create();
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.appId = appId;
    }

    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", restApiKey)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    /** Web flow: authorization code -> Kakao access token. */
    public String exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        try {
            TokenResponse res = restClient.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(TokenResponse.class);
            if (res == null || res.accessToken() == null) {
                throw new AuthException("Kakao token exchange returned no access token");
            }
            return res.accessToken();
        } catch (RestClientException e) {
            throw new AuthException("Kakao token exchange failed", e);
        }
    }

    /** Mobile flow: reject tokens issued for a different Kakao app (only when kakao.app-id is set). */
    public void verifyAppId(String accessToken) {
        if (appId.isBlank()) {
            return;
        }
        try {
            TokenInfo info = restClient.get().uri(TOKEN_INFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(TokenInfo.class);
            if (info == null || !appId.equals(info.appId())) {
                throw new AuthException("Kakao token was issued for a different app");
            }
        } catch (RestClientException e) {
            throw new AuthException("Invalid Kakao access token", e);
        }
    }

    public KakaoUser fetchUser(String accessToken) {
        try {
            UserMe me = restClient.get().uri(USER_ME_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(UserMe.class);
            if (me == null || me.id() == null) {
                throw new AuthException("Kakao user info was empty");
            }
            UserMe.Account account = me.kakaoAccount();
            UserMe.Profile profile = account == null ? null : account.profile();
            return new KakaoUser(
                    me.id(),
                    profile == null ? null : profile.nickname(),
                    account == null ? null : account.email(),
                    profile == null ? null : profile.profileImageUrl());
        } catch (RestClientException e) {
            throw new AuthException("Invalid Kakao access token", e);
        }
    }
}
