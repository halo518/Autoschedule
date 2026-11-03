package com.example.moneybug.repository;

import com.example.moneybug.entity.WorkerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkerAvailabilityRepository extends JpaRepository<WorkerAvailability, Long> {

    List<WorkerAvailability> findByWorkerIdIn(Collection<Long> workerIds);

    void deleteByWorkerId(Long workerId);
}
