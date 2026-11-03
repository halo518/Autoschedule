package com.example.autoschedule.service;

import com.example.autoschedule.entity.Shift;
import com.example.autoschedule.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShiftRulesTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);

    private static WorkBlock block(long worker, boolean minor, LocalDate d, int start, int end, int brk) {
        return new WorkBlock("b" + worker + d + start, worker, "w" + worker, minor, d, start, end, brk, Shift.Status.NORMAL);
    }

    @Test
    void legalBreak() {
        assertEquals(0, BreakCalculator.legalMinimum(3 * 60 + 59));
        assertEquals(30, BreakCalculator.legalMinimum(4 * 60));
        assertEquals(30, BreakCalculator.legalMinimum(6 * 60));
        assertEquals(30, BreakCalculator.legalMinimum(8 * 60));       // 8h 체류 = 7.5h 근로 + 30m
        assertEquals(60, BreakCalculator.legalMinimum(8 * 60 + 30));  // 8h 근로 이상이면 1h
        assertEquals(60, BreakCalculator.legalMinimum(9 * 60));
    }

    @Test
    void manualBreakBelowLegalIsRaisedAndLargerIsKept() {
        assertEquals(60, BreakCalculator.resolve(9 * 60, 0, null));
        assertEquals(90, BreakCalculator.resolve(9 * 60, 90, null));
        assertEquals(30, BreakCalculator.resolve(3 * 60, 0, 30)); // 파트에 설정된 휴게
    }

    @Test
    void breakStartIsSnappedAndClamped() {
        assertNull(BreakCalculator.placeBreakStart(9 * 60, 12 * 60, 0, null));
        assertEquals(10 * 60 + 30, BreakCalculator.placeBreakStart(8 * 60, 14 * 60, 30, null));
        // 희망값이 범위 안이면 유지, 밖이면 보정
        assertEquals(11 * 60, BreakCalculator.placeBreakStart(8 * 60, 14 * 60, 30, 11 * 60));
        assertEquals(10 * 60 + 30, BreakCalculator.placeBreakStart(8 * 60, 14 * 60, 30, 8 * 60));
    }

    @Test
    void overlapIsRejectedButAdjacentIsAllowed() {
        assertDoesNotThrow(() -> ShiftValidator.validate(List.of(
                block(1, false, MON, 9 * 60, 12 * 60, 0), block(1, false, MON, 12 * 60, 15 * 60, 0))));
        ApiException e = assertThrows(ApiException.class, () -> ShiftValidator.validate(List.of(
                block(1, false, MON, 9 * 60, 13 * 60, 30), block(1, false, MON, 12 * 60, 15 * 60, 0))));
        assertEquals(409, e.getStatus().value());
        // 다른 사람과는 겹쳐도 된다
        assertDoesNotThrow(() -> ShiftValidator.validate(List.of(
                block(1, false, MON, 9 * 60, 13 * 60, 30), block(2, false, MON, 9 * 60, 13 * 60, 30))));
    }

    @Test
    void workOnLeaveDayIsRejected() {
        WorkBlock leave = new WorkBlock("l", 1L, "w1", false, MON, null, null, 0, Shift.Status.ANNUAL_LEAVE);
        assertThrows(ApiException.class, () ->
                ShiftValidator.validate(List.of(leave, block(1, false, MON, 9 * 60, 12 * 60, 0))));
    }

    @Test
    void minorNightAndDailyLimit() {
        assertDoesNotThrow(() -> ShiftValidator.validate(List.of(block(1, true, MON, 16 * 60, 22 * 60, 30))));
        assertThrows(ApiException.class, () ->
                ShiftValidator.validate(List.of(block(1, true, MON, 17 * 60, 22 * 60 + 15, 30))));
        // 실근로 7h 허용, 7h 초과 불가 (체류 8h30 - 휴게 60 = 7h30)
        assertDoesNotThrow(() -> ShiftValidator.validate(List.of(block(1, true, MON, 9 * 60, 17 * 60 + 30, 90))));
        assertThrows(ApiException.class, () ->
                ShiftValidator.validate(List.of(block(1, true, MON, 9 * 60, 17 * 60 + 30, 60))));
        // 성인은 제한 없음
        assertDoesNotThrow(() -> ShiftValidator.validate(List.of(block(1, false, MON, 9 * 60, 23 * 60, 60))));
    }

    @Test
    void minorWeeklyLimit() {
        // 월~금 하루 7h = 35h 허용, 토요일 1h 추가하면 초과
        List<WorkBlock> week = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) week.add(block(1, true, MON.plusDays(i), 9 * 60, 16 * 60 + 30, 30));
        assertDoesNotThrow(() -> ShiftValidator.validate(week));
        week.add(block(1, true, MON.plusDays(5), 9 * 60, 11 * 60, 0));
        assertThrows(ApiException.class, () -> ShiftValidator.validate(week));
    }
}