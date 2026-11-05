package com.example.autoschedule.service;

import com.example.autoschedule.dto.StoreDto;
import com.example.autoschedule.entity.Part;
import com.example.autoschedule.entity.Store;
import com.example.autoschedule.entity.StoreDemand;
import com.example.autoschedule.entity.StoreHours;
import com.example.autoschedule.entity.Weekday;
import com.example.autoschedule.repository.StoreRepository;
import com.example.autoschedule.repository.UserRepository;
import com.example.autoschedule.repository.WorkerAvailabilityRepository;
import com.example.autoschedule.util.TimeCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 로그인한 사용자의 매장(사용자당 1개) 하나를 프론트의 Store 묶음으로 읽고 쓴다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private static final int DAYS = Weekday.values().length;
    private static final int DEFAULT_BREAK_MINUTES = 30;
    private static final int DEFAULT_MINIMUM_WAGE = 10320;

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final WorkerAvailabilityRepository availabilityRepository;

    /** 매장이 없는 사용자는 프론트 기본값으로 매장을 만들어 돌려준다. */
    @Transactional
    public StoreDto.Response get(Long userId) {
        return toResponse(ensureStore(userId));
    }

    @Transactional
    public Store ensureStore(Long userId) {
        return storeRepository.findByUserId(userId).orElseGet(() -> create(userId, defaultRequest()));
    }

    /** 매장이 없으면 만들고 있으면 수정한다. 파트·필요 인원·영업시간은 요청 내용으로 맞춘다. */
    @Transactional
    public StoreDto.Response save(Long userId, StoreDto.Request request) {
        Store store = storeRepository.findByUserId(userId).orElse(null);
        return toResponse(store == null ? create(userId, request) : update(store, request));
    }

    // ---- 쓰기 ----

    private Store create(Long userId, StoreDto.Request request) {
        Store store = new Store();
        store.setUser(userRepository.getReferenceById(userId));
        return update(store, request);
    }

    private Store update(Store store, StoreDto.Request r) {
        store.setName(r.name().trim());
        store.setBranch(r.branch() == null || r.branch().isBlank() ? null : r.branch().trim());
        store.setScale(r.size());
        store.setMinimumWage(r.minimumWage());

        replaceHours(store, r);
        reconcileParts(store, r.parts());
        replaceDemand(store, r.demand());

        Store saved = storeRepository.saveAndFlush(store);
        // 파트가 지워지면 DB 가 worker_availability.part_id 를 NULL 로 바꾸므로, 시간도 없는 빈 행은 정리한다.
        availabilityRepository.deleteByWorkerStoreIdAndPartIsNullAndStartTimeIsNull(saved.getId());
        return saved;
    }

    /** 평일(월~금)과 주말(토·일) 영업시간을 요일별 행으로 풀어 저장한다. 공휴일은 프론트가 처리한다. */
    private void replaceHours(Store store, StoreDto.Request r) {
        LocalTime open = TimeCodec.start(r.open());
        LocalTime close = TimeCodec.end(open, r.close());
        LocalTime weekendOpen = TimeCodec.start(r.weekendOpen());
        LocalTime weekendClose = TimeCodec.end(weekendOpen, r.weekendClose());

        store.getHours().clear();
        for (Weekday day : Weekday.values()) {
            boolean weekend = day == Weekday.SAT || day == Weekday.SUN;
            StoreHours hours = new StoreHours();
            hours.setStore(store);
            hours.setDayOfWeek(day);
            hours.setOpenTime(weekend ? weekendOpen : open);
            hours.setCloseTime(weekend ? weekendClose : close);
            store.getHours().add(hours);
        }
    }

    /**
     * 요청의 파트를 기준으로 맞춘다: 내 매장에 있는 숫자 id 는 수정, 그 외(새 id·임시 id)는 추가, 요청에 없는 기존 파트는 삭제.
     * 삭제된 파트를 가리키던 가능 파트·근무는 DB 의 ON DELETE SET NULL 로 비워진다.
     */
    private void reconcileParts(Store store, List<StoreDto.PartItem> items) {
        Map<Long, Part> existing = store.getParts().stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        Set<Part> keep = new HashSet<>();
        for (StoreDto.PartItem item : items) {
            Part part = existing.get(parseId(item.id()));
            if (part == null) {
                part = new Part();
                part.setStore(store);
                store.getParts().add(part);
            }
            LocalTime start = TimeCodec.start(item.start());
            part.setName(item.name().trim());
            part.setStartTime(start);
            part.setEndTime(TimeCodec.end(start, item.end()));
            part.setBreakMinutes(item.breakMinutes() == null ? DEFAULT_BREAK_MINUTES : item.breakMinutes());
            keep.add(part);
        }
        store.getParts().removeIf(p -> !keep.contains(p));
    }

    /** days 가 없으면 매일, 있으면 그 요일마다 한 행씩 저장한다. */
    private void replaceDemand(Store store, List<StoreDto.DemandItem> items) {
        store.getDemand().clear();
        for (StoreDto.DemandItem item : items) {
            LocalTime start = TimeCodec.start(item.start());
            LocalTime end = TimeCodec.end(start, item.end());
            List<Integer> days = item.days() == null || item.days().isEmpty()
                    ? java.util.stream.IntStream.range(0, DAYS).boxed().toList()
                    : item.days().stream().distinct().sorted().toList();
            for (int day : days) {
                StoreDemand demand = new StoreDemand();
                demand.setStore(store);
                demand.setDayOfWeek(Weekday.values()[day]);
                demand.setStartTime(start);
                demand.setEndTime(end);
                demand.setRequiredCount(item.required());
                store.getDemand().add(demand);
            }
        }
    }

    /** 프론트 기본값(defaults.ts)과 같은 매장. */
    private static StoreDto.Request defaultRequest() {
        return new StoreDto.Request("내 매장", null, Store.Scale.OVER_5, "08:00", "22:00", "08:00", "22:00",
                List.of(new StoreDto.PartItem(null, "오픈", null, "08:00", "12:00", DEFAULT_BREAK_MINUTES),
                        new StoreDto.PartItem(null, "미들", null, "12:00", "17:00", DEFAULT_BREAK_MINUTES),
                        new StoreDto.PartItem(null, "마감", null, "17:00", "22:00", DEFAULT_BREAK_MINUTES)),
                List.of(new StoreDto.DemandItem("08:00", "12:00", 1, null),
                        new StoreDto.DemandItem("12:00", "14:00", 2, null),
                        new StoreDto.DemandItem("14:00", "17:00", 1, null),
                        new StoreDto.DemandItem("17:00", "20:00", 2, null),
                        new StoreDto.DemandItem("20:00", "22:00", 1, null)),
                DEFAULT_MINIMUM_WAGE);
    }

    // ---- 읽기 ----

    private StoreDto.Response toResponse(Store store) {
        Map<Weekday, StoreHours> hours = store.getHours().stream()
                .collect(Collectors.toMap(StoreHours::getDayOfWeek, Function.identity()));
        StoreHours weekday = hours.getOrDefault(Weekday.MON, hours.values().stream().findFirst().orElse(null));
        StoreHours weekend = hours.getOrDefault(Weekday.SAT, hours.getOrDefault(Weekday.SUN, weekday));

        List<Part> parts = store.getParts().stream().sorted(Comparator.comparing(Part::getStartTime)).toList();
        List<StoreDto.PartItem> partItems = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            partItems.add(new StoreDto.PartItem(String.valueOf(p.getId()), p.getName(), tone(i, parts.size()),
                    TimeCodec.formatStart(p.getStartTime()), TimeCodec.formatEnd(p.getStartTime(), p.getEndTime()),
                    p.getBreakMinutes()));
        }

        return new StoreDto.Response(store.getId(), store.getName(), store.getBranch() == null ? "" : store.getBranch(),
                store.getScale(),
                open(weekday), close(weekday), open(weekend), close(weekend),
                partItems, demandItems(store), store.getMinimumWage());
    }

    /** 같은 (시작, 종료, 인원)인 요일 행들을 days 로 묶는다. 7일 전부면 days 를 생략(매일)한다. */
    private List<StoreDto.DemandItem> demandItems(Store store) {
        List<StoreDemand> rows = store.getDemand().stream()
                .sorted(Comparator.comparing(StoreDemand::getStartTime)
                        .thenComparing(StoreDemand::getEndTime)
                        .thenComparing(StoreDemand::getRequiredCount)
                        .thenComparing(StoreDemand::getDayOfWeek))
                .toList();
        Map<String, List<StoreDemand>> grouped = new LinkedHashMap<>();
        for (StoreDemand row : rows) {
            grouped.computeIfAbsent(row.getStartTime() + "|" + row.getEndTime() + "|" + row.getRequiredCount(),
                    k -> new ArrayList<>()).add(row);
        }
        return grouped.values().stream().map(group -> {
            StoreDemand first = group.get(0);
            List<Integer> days = group.stream().map(g -> g.getDayOfWeek().ordinal()).toList();
            return new StoreDto.DemandItem(TimeCodec.formatStart(first.getStartTime()),
                    TimeCodec.formatEnd(first.getStartTime(), first.getEndTime()),
                    first.getRequiredCount(), days.size() == DAYS ? null : days);
        }).toList();
    }

    // ---- 내부 ----

    /** shortcut: parts 에 tone 컬럼이 없어 시작 시각 순서로 오픈/미들/마감을 정한다. */
    private static String tone(int index, int size) {
        if (index == 0) {
            return "open";
        }
        return index == size - 1 ? "close" : "middle";
    }

    private static String open(StoreHours h) {
        return h == null ? "09:00" : TimeCodec.formatStart(h.getOpenTime());
    }

    private static String close(StoreHours h) {
        return h == null ? "22:00" : TimeCodec.formatEnd(h.getOpenTime(), h.getCloseTime());
    }

    /** 숫자가 아니면(새 파트의 임시 id 포함) null → 새 파트로 취급한다. */
    private static Long parseId(String id) {
        try {
            return id == null ? null : Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
