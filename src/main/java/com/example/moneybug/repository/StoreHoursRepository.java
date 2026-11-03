package com.example.moneybug.repository;

import com.example.moneybug.entity.StoreHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreHoursRepository extends JpaRepository<StoreHours, Long> {

    List<StoreHours> findByStoreId(Long storeId);

    void deleteByStoreId(Long storeId);
}
