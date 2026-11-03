package com.example.moneybug.service;

import com.example.moneybug.dto.StaffDto;
import com.example.moneybug.entity.Part;
import com.example.moneybug.entity.Store;
import com.example.moneybug.entity.Weekday;
import com.example.moneybug.entity.Worker;
import com.example.moneybug.entity.WorkerAvailability;
import com.example.moneybug.exception.BadRequestException;
import com.example.moneybug.exception.NotFoundException;
import com.example.moneybug.repository.PartRepository;
import com.example.moneybug.repository.StoreRepository;
import com.example.moneybug.repository.WorkerAvailabilityRepository;
import com.example.moneybug.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 로그인한 사용자의 매장에 속한 직원/알바생만 다룬다. 남의 구성원은 없는 것처럼 404 로 응답한다. */
@Service
@Transactional(readOnly = true)
public class StaffService {

    private static final int DAYS = Weekday.values().length;

    private final StoreRepository storeRepository;
    private final WorkerRepository workerRepository;
    private final WorkerAvailabilityRepository availabilityRepository;
    private final PartRepository partRepository;

    public StaffService(StoreRepository storeRepository, WorkerRepository workerRepository,
                        WorkerAvailabilityRepository availabilityRepository, PartRepository partRepository) {
        this.storeRepository = storeRepository;
        this.workerRepository = workerRepository;
        this.availabilityRepository = availabilityRepository;
        this.partRepository = partRepository;
    }

    /** 매장이 아직 없으면 빈 목록을 돌려준다. */
    public List<StaffDto.Response> list(Long userId) {
        Optional<Store> store = storeRepository.findFirstByUserIdOrderById(userId);
        if (store.isEmpty()) {
            return List.of();
        }
        List<Worker> workers = workerRepository.findByStoreIdOrderById(store.get().getId());
        if (workers.isEmpty()) {
            return List.of();
        }
        Map<Long, List<WorkerAvailability>> rows = availabilityRepository
                .findByWorkerIdIn(workers.stream().map(Worker::getId).toList()).stream()
                .collect(Collectors.groupingBy(a -> a.getWorker().getId()));
        return workers.stream().map(w -> toResponse(w, rows.getOrDefault(w.getId(), List.of()))).toList();
    }

    @Transactional
    public StaffDto.Response create(Long userId, StaffDto.Request request) {
        Store store = storeRepository.findFirstByUserIdOrderById(userId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
        Worker worker = new Worker();
        worker.setStore(store);
        return write(store, worker, request);
    }

    @Transactional
    public StaffDto.Response update(Long userId, Long workerId, StaffDto.Request request) {
        Worker worker = owned(userId, workerId);
        return write(worker.getStore(), worker, request);
    }

    /** worker_availability 는 DB 의 ON DELETE CASCADE 로 함께 지워진다. */
    @Transactional
    public void delete(Long userId, Long workerId) {
        workerRepository.delete(owned(userId, workerId));
    }

    // ---- 내부 ----

    private Worker owned(Long userId, Long workerId) {
        return workerRepository.findByIdAndStoreUserId(workerId, userId)
                .orElseThrow(() -> new NotFoundException("Staff not found"));
    }

    private StaffDto.Response write(Store store, Worker worker, StaffDto.Request r) {
        boolean partTime = r.employmentType() == Worker.EmploymentType.PART_TIME;

        worker.setName(r.name().trim());
        worker.setEmploymentType(r.employmentType());
        worker.setTitle(blankToNull(r.title()));
        worker.setHourlyWage(r.hourlyWage());
        worker.setPhone(blankToNull(r.phone()));
        worker.setMinor(Boolean.TRUE.equals(r.isMinor()));
        // 직원은 계약 주 근로시간, 알바생은 희망 주간 근무시간만 쓴다.
        worker.setContractWeeklyHours(partTime ? null : r.contractWeeklyHours());
        Integer min = null;
        Integer max = null;
        if (partTime && r.preferredWeeklyHours() != null) {
            if (r.preferredWeeklyHours().size() != 2 || r.preferredWeeklyHours().get(0) > r.preferredWeeklyHours().get(1)) {
                throw new BadRequestException("preferredWeeklyHours must be [min, max]");
            }
            min = r.preferredWeeklyHours().get(0);
            max = r.preferredWeeklyHours().get(1);
        }
        worker.setMinWeeklyHours(min);
        worker.setMaxWeeklyHours(max);
        workerRepository.save(worker);

        List<WorkerAvailability> rows = partTime
                ? partRows(store, worker, r.availableParts())
                : fixedRows(worker, r.workHours());
        availabilityRepository.deleteByWorkerId(worker.getId());
        availabilityRepository.flush();
        availabilityRepository.saveAll(rows);
        return toResponse(worker, rows);
    }

    /** 알바생: 요일별 가능 파트 id 목록 → (요일, 파트) 한 줄씩. */
    private List<WorkerAvailability> partRows(Store store, Worker worker, List<List<String>> availableParts) {
        List<WorkerAvailability> rows = new ArrayList<>();
        if (availableParts == null) {
            return rows;
        }
        if (availableParts.size() > DAYS) {
            throw new BadRequestException("availableParts must have at most 7 days");
        }
        Map<Long, Part> parts = partRepository.findByStoreIdOrderByStartTime(store.getId()).stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        for (int day = 0; day < availableParts.size(); day++) {
            List<String> ids = availableParts.get(day);
            if (ids == null) {
                continue;
            }
            Set<Long> seen = new HashSet<>();
            for (String id : ids) {
                Part part = parts.get(parseId(id));
                if (part == null) {
                    throw new BadRequestException("Unknown part: " + id);
                }
                if (seen.add(part.getId())) {
                    WorkerAvailability row = new WorkerAvailability();
                    row.setWorker(worker);
                    row.setDayOfWeek(Weekday.values()[day]);
                    row.setPart(part);
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** 직원: 요일별 고정 출퇴근 시각(null = 쉬는 날) → 있는 요일만 한 줄씩. */
    private List<WorkerAvailability> fixedRows(Worker worker, List<StaffDto.WorkTime> workHours) {
        List<WorkerAvailability> rows = new ArrayList<>();
        if (workHours == null) {
            return rows;
        }
        if (workHours.size() > DAYS) {
            throw new BadRequestException("workHours must have at most 7 days");
        }
        for (int day = 0; day < workHours.size(); day++) {
            StaffDto.WorkTime time = workHours.get(day);
            if (time == null) {
                continue;
            }
            WorkerAvailability row = new WorkerAvailability();
            row.setWorker(worker);
            row.setDayOfWeek(Weekday.values()[day]);
            row.setStartTime(LocalTime.parse(time.start()));
            row.setEndTime(LocalTime.parse(time.end()));
            rows.add(row);
        }
        return rows;
    }

    private StaffDto.Response toResponse(Worker w, List<WorkerAvailability> rows) {
        List<List<String>> availableParts = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            availableParts.add(new ArrayList<>());
        }
        List<StaffDto.WorkTime> workHours = null;
        for (WorkerAvailability row : rows) {
            int day = row.getDayOfWeek().ordinal();
            if (row.getPart() != null) {
                availableParts.get(day).add(String.valueOf(row.getPart().getId()));
            } else if (row.getStartTime() != null && row.getEndTime() != null) {
                if (workHours == null) {
                    workHours = new ArrayList<>(java.util.Collections.nCopies(DAYS, null));
                }
                workHours.set(day, new StaffDto.WorkTime(row.getStartTime().toString(), row.getEndTime().toString()));
            }
        }
        List<Integer> preferred = w.getMinWeeklyHours() != null && w.getMaxWeeklyHours() != null
                ? List.of(w.getMinWeeklyHours(), w.getMaxWeeklyHours()) : null;
        // 프론트 타입이 title/phone 을 string 으로 보므로 비어 있으면 빈 문자열로 돌려준다.
        return new StaffDto.Response(w.getId(), w.getName(), w.getEmploymentType(), orEmpty(w.getTitle()), w.getHourlyWage(),
                w.isMinor(), w.getContractWeeklyHours(), preferred, availableParts, workHours, orEmpty(w.getPhone()));
    }

    private static Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid part id: " + id);
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
