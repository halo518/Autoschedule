# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MoneyBug is a Spring Boot 4.1.1 web app (Java 17 toolchain, Gradle, group `com.example`). It is currently a bare scaffold: only `MoneyBugApplication` and a context-load test exist under `com.example.moneybug`. Not a git repository.

Stack: Spring Data JPA (Hibernate) + Spring Web MVC (`spring-boot-starter-webmvc`) + MySQL (`mysql-connector-j`) + Lombok (annotation processor is configured for both main and test).

## Commands

Use the Gradle wrapper (on Windows PowerShell: `.\gradlew.bat`):

- Run app: `.\gradlew.bat bootRun`
- Build: `.\gradlew.bat build`
- All tests: `.\gradlew.bat test`
- Single test class: `.\gradlew.bat test --tests "com.example.moneybug.MoneyBugApplicationTests"`
- Single test method: `.\gradlew.bat test --tests "com.example.moneybug.MoneyBugApplicationTests.methodName"`

No lint/formatter is configured.

## Runtime configuration

`src/main/resources/application.properties` points at a local MySQL: `jdbc:mysql://localhost:3306/moneybug` (timezone `Asia/Seoul`, user `root`). A MySQL instance with a `moneybug` database must be running, otherwise the app and the `@SpringBootTest` context-load test fail at startup (the test has no DB override or embedded DB).

`spring.jpa.hibernate.ddl-auto=update` means entity classes drive the schema; there are no migration scripts. SQL logging is on (`show-sql`, `format_sql`).

## Auth architecture

Kakao login for web and mobile converges in `auth/AuthService`: get a Kakao access token -> `KakaoClient.fetchUser` -> upsert `user/User` by `kakaoId` -> issue our own JWT access token (`auth/jwt/JwtProvider`, stateless `SecurityConfig` + `JwtAuthenticationFilter`) and an opaque refresh token (only its SHA-256 is stored in `RefreshToken`; rotated on `/api/auth/refresh`).
- Mobile: `POST /api/auth/kakao/token` with the Kakao SDK access token.
- Web: `GET /api/auth/kakao/login` -> Kakao -> `GET /api/auth/kakao/callback` (state kept in an HttpOnly cookie).

Required env vars: `JWT_SECRET` (>= 32 bytes; the app won't start without it), `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, optional `KAKAO_APP_ID` (rejects mobile tokens from other Kakao apps). `@SpringBootTest` tests therefore also need `JWT_SECRET` set. jjwt uses the `orgjson` serializer on purpose (Boot 4 ships Jackson 3).

The comments in `application.properties` are Korean and appear mojibake'd (`?`) in the file; preserve the file's encoding when editing it.
