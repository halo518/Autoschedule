package com.example.autoschedule.repository;

import com.example.autoschedule.entity.WorkerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkerAvailabilityRepository extends JpaRepository<WorkerAvailability, Long> {

    List<WorkerAvailability> findByWorkerId(Long workerId);

    /** AI 배치 시 매장 전체 구성원의 가능 시간을 한 번에 조회 */
    List<WorkerAvailability> findByWorkerIdIn(Collection<Long> workerIds);

    /** 파트가 삭제되면 DB 가 part_id 를 NULL 로 바꾸므로, 파트도 시간도 없는 빈 행을 정리한다. */
    void deleteByWorkerStoreIdAndPartIsNullAndStartTimeIsNull(Long storeId);
}