package com.example.moneybug.repository;

import com.example.moneybug.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartRepository extends JpaRepository<Part, Long> {

    List<Part> findByStoreIdOrderByStartTime(Long storeId);
}
