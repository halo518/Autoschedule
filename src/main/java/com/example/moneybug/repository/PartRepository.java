package com.example.moneybug.repository;

import com.example.moneybug.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long> {

    List<Part> findByStoreIdOrderByStartTime(Long storeId);

    /** 파트가 해당 사용자의 매장 소속일 때만 조회한다. */
    Optional<Part> findByIdAndStoreUserId(Long id, Long userId);
}
