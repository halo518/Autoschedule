package com.example.moneybug.repository;

import com.example.moneybug.entity.StoreDemand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreDemandRepository extends JpaRepository<StoreDemand, Long> {

    List<StoreDemand> findByStoreId(Long storeId);

    void deleteByStoreId(Long storeId);
}
