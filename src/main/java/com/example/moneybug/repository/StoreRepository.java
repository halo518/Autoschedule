package com.example.moneybug.repository;

import com.example.moneybug.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** 사용자 1명이 매장 1개를 가진다. */
    Optional<Store> findFirstByUserIdOrderById(Long userId);
}
