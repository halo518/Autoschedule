package com.example.autoschedule.service;

import com.example.autoschedule.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 근무 블록 하드 제약.
 * <ol>
 *   <li>같은 사람의 근무끼리 시간이 겹치지 않는다. 연차/휴무인 날에는 근무가 없다.</li>
 *   <li>연소자(만 18세 미만): 22:00 이후 근무 불가, 1일 7시간(실근로) 초과 불가, 주 35시간 초과 불가.</li>
 * </ol>
 * 주 35시간은 이 근무표(한 달) 안의 월~일 합계만 본다.
 * {@link #validate} 는 첫 위반에서 예외를 던지고, {@link #collect} 는 위반을 모두 모아 돌려준다(AI 보정 루프용).
 */
public final class ShiftValidator {

    static final int MINOR_LAST_END = 22 * 60;
    static final int MINOR_DAILY_MAX = 7 * 60;
    static final int MINOR_WEEKLY_MAX = 35 * 60;

    /**
     * @param blockIds 위반에 관련된 블록 id. 하나를 버려 위반을 풀어야 한다면 마지막 id 를 버린다.
     */
    public record Violation(HttpStatus status, String message, List<String> blockIds) {}

    private ShiftValidator() {}

    public static void validate(Collection<WorkBlock> blocks) {
        List<Violation> violations = collect(blocks);
        if (!violations.isEmpty()) {
            Violation v = violations.get(0);
            throw new ApiException(v.status(), v.message());
        }
    }

    public static List<Violation> collect(Collection<WorkBlock> blocks) {
        List<Violation> out = new ArrayList<>();
        Map<Long, List<WorkBlock>> byWorker = blocks.stream()
                .collect(Collectors.groupingBy(WorkBlock::workerId, LinkedHashMap::new, Collectors.toList()));
        byWorker.values().forEach(list -> collectWorker(list, out));
        return out;
    }

    private static void collectWorker(List<WorkBlock> list, List<Violation> out) {
        Map<LocalDate, List<WorkBlock>> byDate = list.stream()
                .collect(Collectors.groupingBy(WorkBlock::date, TreeMap::new, Collectors.toList()));
        byDate.forEach((date, day) -> collectDay(date, day, out));

        WorkBlock any = list.get(0);
        if (any.minor()) {
            Map<LocalDate, List<WorkBlock>> weeks = list.stream().filter(WorkBlock::isWorking)
                    .collect(Collectors.groupingBy(b -> b.date().with(DayOfWeek.MONDAY), TreeMap::new, Collectors.toList()));
            weeks.forEach((monday, blocks) -> {
                int net = blocks.stream().mapToInt(WorkBlock::netMinutes).sum();
                if (net > MINOR_WEEKLY_MAX) {
                    List<String> ids = blocks.stream()
                            .sorted(Comparator.comparing(WorkBlock::date)
                                    .thenComparing(b -> b.startMin() == null ? 0 : b.startMin()))
                            .map(WorkBlock::id).toList();
                    out.add(new Violation(HttpStatus.BAD_REQUEST, any.workerName()
                            + "님은 연소자라 주 35시간을 초과해 근무할 수 없습니다. (" + monday + " 주)", ids));
                }
            });
        }
    }

    private static void collectDay(LocalDate date, List<WorkBlock> day, List<Violation> out) {
        List<WorkBlock> working = day.stream().filter(WorkBlock::isWorking).toList();
        String name = day.get(0).workerName();

        if (!working.isEmpty() && day.stream().anyMatch(WorkBlock::isAbsence)) {
            out.add(new Violation(HttpStatus.CONFLICT,
                    name + "님은 " + date + "에 연차/휴무라 근무를 배정할 수 없습니다.",
                    working.stream().map(WorkBlock::id).toList()));
        }

        List<WorkBlock> timed = working.stream().filter(WorkBlock::hasTime)
                .sorted(Comparator.comparingInt(WorkBlock::startMin)).toList();
        int maxEnd = Integer.MIN_VALUE;
        WorkBlock prev = null;
        for (WorkBlock b : timed) {
            if (prev != null && b.startMin() < maxEnd) {
                out.add(new Violation(HttpStatus.CONFLICT,
                        name + "님의 " + date + " 근무(" + Minutes.format(b.startMin()) + "~" + Minutes.format(b.endMin())
                                + ")가 다른 근무(" + Minutes.format(prev.startMin()) + "~" + Minutes.format(prev.endMin())
                                + ")와 겹칩니다.", List.of(b.id())));
            }
            if (b.endMin() > maxEnd) {
                maxEnd = b.endMin();
                prev = b;
            }
        }

        if (day.get(0).minor()) {
            for (WorkBlock b : timed) {
                if (b.endMin() > MINOR_LAST_END) {
                    out.add(new Violation(HttpStatus.BAD_REQUEST,
                            name + "님은 연소자라 22:00 이후에 근무할 수 없습니다. (" + date + ")", List.of(b.id())));
                }
            }
            int net = timed.stream().mapToInt(WorkBlock::netMinutes).sum();
            if (net > MINOR_DAILY_MAX) {
                out.add(new Violation(HttpStatus.BAD_REQUEST,
                        name + "님은 연소자라 하루 7시간을 초과해 근무할 수 없습니다. (" + date + ")",
                        timed.stream().map(WorkBlock::id).toList()));
            }
        }
    }
}