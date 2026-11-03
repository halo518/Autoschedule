package com.example.autoschedule.repository;

import com.example.autoschedule.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** "내 매장" (/stores/me) */
    Optional<Store> findByUserId(Long userId);
}