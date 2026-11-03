package com.example.moneybug.repository;

import com.example.moneybug.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    List<Worker> findByStoreIdOrderById(Long storeId);

    /** 구성원이 해당 사용자의 매장 소속일 때만 조회한다. */
    Optional<Worker> findByIdAndStoreUserId(Long id, Long userId);
}
