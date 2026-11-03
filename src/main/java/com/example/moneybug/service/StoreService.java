package com.example.moneybug.service;

import com.example.moneybug.dto.PartDto;
import com.example.moneybug.dto.StoreDemandDto;
import com.example.moneybug.dto.StoreDto;
import com.example.moneybug.dto.StoreHoursDto;
import com.example.moneybug.entity.Part;
import com.example.moneybug.entity.Store;
import com.example.moneybug.entity.StoreDemand;
import com.example.moneybug.entity.StoreHours;
import com.example.moneybug.entity.Weekday;
import com.example.moneybug.exception.BadRequestException;
import com.example.moneybug.exception.NotFoundException;
import com.example.moneybug.repository.PartRepository;
import com.example.moneybug.repository.StoreDemandRepository;
import com.example.moneybug.repository.StoreHoursRepository;
import com.example.moneybug.repository.StoreRepository;
import com.example.moneybug.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 로그인한 사용자(userId)의 매장(사용자당 1개)만 다룬다. 남의 파트는 없는 것처럼 404 로 응답한다. */
@Service
@Transactional(readOnly = true)
public class StoreService {

    private static final int DEFAULT_BREAK_MINUTES = 30;

    private final StoreRepository storeRepository;
    private final StoreHoursRepository storeHoursRepository;
    private final StoreDemandRepository storeDemandRepository;
    private final PartRepository partRepository;
    private final UserRepository userRepository;

    public StoreService(StoreRepository storeRepository, StoreHoursRepository storeHoursRepository,
                        StoreDemandRepository storeDemandRepository, PartRepository partRepository,
                        UserRepository userRepository) {
        this.storeRepository = storeRepository;
        this.storeHoursRepository = storeHoursRepository;
        this.storeDemandRepository = storeDemandRepository;
        this.partRepository = partRepository;
        this.userRepository = userRepository;
    }

    // ---- 내 매장 ----

    public StoreDto.Response get(Long userId) {
        return StoreDto.Response.from(mine(userId));
    }

    /** 매장이 없으면 새로 만들고, 있으면 수정한다. */
    @Transactional
    public StoreDto.Response save(Long userId, StoreDto.Request request) {
        Store store = storeRepository.findFirstByUserIdOrderById(userId).orElseGet(() -> {
            Store created = new Store();
            created.setUser(userRepository.getReferenceById(userId));
            return created;
        });
        apply(store, request);
        return StoreDto.Response.from(storeRepository.save(store));
    }

    // ---- 요일별 영업시간 ----

    public List<StoreHoursDto.Response> hours(Long userId) {
        Store store = mine(userId);
        return storeHoursRepository.findByStoreId(store.getId()).stream()
                .sorted((a, b) -> a.getDayOfWeek().compareTo(b.getDayOfWeek()))
                .map(StoreHoursDto.Response::from).toList();
    }

    @Transactional
    public List<StoreHoursDto.Response> replaceHours(Long userId, StoreHoursDto.ReplaceRequest request) {
        Store store = mine(userId);
        Set<Weekday> seen = EnumSet.noneOf(Weekday.class);
        for (StoreHoursDto.Item item : request.getHours()) {
            if (!seen.add(item.getDayOfWeek())) {
                throw new BadRequestException("Duplicate day: " + item.getDayOfWeek());
            }
        }
        storeHoursRepository.deleteByStoreId(store.getId());
        storeHoursRepository.flush();
        List<StoreHours> saved = storeHoursRepository.saveAll(request.getHours().stream().map(item -> {
            StoreHours hours = new StoreHours();
            hours.setStore(store);
            hours.setDayOfWeek(item.getDayOfWeek());
            hours.setOpenTime(item.getOpenTime());
            hours.setCloseTime(item.getCloseTime());
            return hours;
        }).toList());
        return saved.stream().sorted((a, b) -> a.getDayOfWeek().compareTo(b.getDayOfWeek()))
                .map(StoreHoursDto.Response::from).toList();
    }

    // ---- 시간대별 필요 인원 ----

    public List<StoreDemandDto.Response> demand(Long userId) {
        Store store = mine(userId);
        return storeDemandRepository.findByStoreId(store.getId()).stream()
                .sorted((a, b) -> {
                    int byDay = a.getDayOfWeek().compareTo(b.getDayOfWeek());
                    return byDay != 0 ? byDay : a.getStartTime().compareTo(b.getStartTime());
                })
                .map(StoreDemandDto.Response::from).toList();
    }

    @Transactional
    public List<StoreDemandDto.Response> replaceDemand(Long userId, StoreDemandDto.ReplaceRequest request) {
        Store store = mine(userId);
        storeDemandRepository.deleteByStoreId(store.getId());
        storeDemandRepository.flush();
        List<StoreDemand> saved = storeDemandRepository.saveAll(request.getDemand().stream().map(item -> {
            StoreDemand demand = new StoreDemand();
            demand.setStore(store);
            demand.setDayOfWeek(item.getDayOfWeek());
            demand.setStartTime(item.getStartTime());
            demand.setEndTime(item.getEndTime());
            demand.setRequiredCount(item.getRequiredCount());
            return demand;
        }).toList());
        return saved.stream().map(StoreDemandDto.Response::from).toList();
    }

    // ---- 근무 파트 ----

    public List<PartDto.Response> parts(Long userId) {
        Store store = mine(userId);
        return partRepository.findByStoreIdOrderByStartTime(store.getId()).stream().map(PartDto.Response::from).toList();
    }

    @Transactional
    public PartDto.Response createPart(Long userId, PartDto.Request request) {
        Part part = new Part();
        part.setStore(mine(userId));
        apply(part, request);
        return PartDto.Response.from(partRepository.save(part));
    }

    @Transactional
    public PartDto.Response updatePart(Long userId, Long partId, PartDto.Request request) {
        Part part = ownedPart(userId, partId);
        apply(part, request);
        return PartDto.Response.from(part);
    }

    @Transactional
    public void deletePart(Long userId, Long partId) {
        partRepository.delete(ownedPart(userId, partId));
    }

    // ---- 내부 ----

    private Store mine(Long userId) {
        return storeRepository.findFirstByUserIdOrderById(userId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Part ownedPart(Long userId, Long partId) {
        return partRepository.findByIdAndStoreUserId(partId, userId)
                .orElseThrow(() -> new NotFoundException("Part not found"));
    }

    private static void apply(Store store, StoreDto.Request request) {
        store.setName(request.getName().trim());
        store.setBranch(request.getBranch() == null || request.getBranch().isBlank() ? null : request.getBranch().trim());
        store.setScale(request.getScale());
        store.setMinimumWage(request.getMinimumWage());
    }

    private static void apply(Part part, PartDto.Request request) {
        part.setName(request.getName().trim());
        part.setStartTime(request.getStartTime());
        part.setEndTime(request.getEndTime());
        part.setBreakMinutes(request.getBreakMinutes() == null ? DEFAULT_BREAK_MINUTES : request.getBreakMinutes());
    }
}
