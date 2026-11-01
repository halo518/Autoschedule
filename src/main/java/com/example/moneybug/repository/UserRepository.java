package com.example.moneybug.repository;

import com.example.moneybug.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    // [수정] AuthService 의 카카오 로그인(upsert)에서 사용
    Optional<User> findByKakaoId(String kakaoId);
}
