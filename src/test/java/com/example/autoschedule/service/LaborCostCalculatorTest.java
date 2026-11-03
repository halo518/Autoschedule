package com.example.autoschedule.service;

import com.example.autoschedule.entity.Shift;
import com.example.autoschedule.entity.Worker;
import com.example.autoschedule.service.LaborCostCalculator.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LaborCostCalculatorTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11); // 11/2 = 월요일, 11/1 = 일요일
    private static final WorkerInfo PART = new WorkerInfo(1, "알바", false, 10000);
    private static final WorkerInfo FULL = new WorkerInfo(2, "직원", true, 12000);

    private static int t(int h, int m) {
        return h * 60 + m;
    }

    private static PayBlock work(long worker, String date, int start, int end, int brk, int brkStart) {
        return new PayBlock(worker, LocalDate.parse(date), start, end, brk, brkStart, false);
    }

    private static PayBlock leave(long worker, String date) {
        return new PayBlock(worker, LocalDate.parse(date), null, null, 0, null, true);
    }

    private static Result calc(boolean premiums, WorkerInfo w, PayBlock... blocks) {
        return LaborCostCalculator.calculate(NOV, premiums, List.of(w), List.of(blocks));
    }

    /** 09:00~14:00 휴게 30 = 실근로 4.5h */
    private static PayBlock day45(long worker, String date) {
        return work(worker, date, t(9, 0), t(14, 0), 30, t(11, 30));
    }

    @Test
    void partTimerOver15HoursGetsWeeklyHolidayPay() {
        Result r = calc(false, PART, day45(1, "2026-11-02"), day45(1, "2026-11-03"),
                day45(1, "2026-11-04"), day45(1, "2026-11-05")); // 18h
        assertEquals(180_000, r.basePay());
        assertEquals(36_000, r.weeklyHolidayPay());          // 10000 x 18/40 x 8
        assertEquals(216_000, r.total());
        assertEquals(1, r.holidayPayTargetCount());
    }

    @Test
    void under15HoursGetsNothingAndExactly15HoursQualifies() {
        Result under = calc(false, PART, day45(1, "2026-11-02"), day45(1, "2026-11-03"), day45(1, "2026-11-04")); // 13.5h
        assertEquals(0, under.weeklyHolidayPay());
        assertEquals(0, under.holidayPayTargetCount());

        PayBlock five = work(1, "2026-11-02", t(9, 0), t(14, 0), 0, 0);
        Result exact = calc(false, PART, five, work(1, "2026-11-03", t(9, 0), t(14, 0), 0, 0),
                work(1, "2026-11-04", t(9, 0), t(14, 0), 0, 0)); // 15h
        assertEquals(30_000, exact.weeklyHolidayPay());      // 10000 x 15/40 x 8
        assertEquals(1, exact.holidayPayTargetCount());
    }

    @Test
    void overtimeAndNightPremiumsOnlyForFiveOrMore() {
        PayBlock longDay = work(1, "2026-11-03", t(8, 0), t(20, 0), 60, t(12, 0)); // 실근로 11h
        Result over5 = calc(true, PART, longDay);
        assertEquals(110_000, over5.basePay());
        assertEquals(15_000, over5.overtimePremium());        // 3h 연장 x 0.5 x 10000
        assertEquals(0, over5.nightPremium());

        Result under5 = calc(false, PART, longDay);
        assertEquals(0, under5.overtimePremium());

        PayBlock night = work(1, "2026-11-03", t(18, 0), t(23, 0), 30, t(20, 0)); // 22~23시 야간 1h
        Result n = calc(true, PART, night);
        assertEquals(5_000, n.nightPremium());
        assertEquals(0, n.overtimePremium());
        assertEquals(0, calc(false, PART, night).nightPremium());
    }

    @Test
    void overtimeAndNightOverlapStackBothPremiums() {
        // 14:00~23:30 휴게 30(18:00) = 실근로 9h: 마지막 1h 연장. 22:00~23:30 야간 1.5h 중 연장 구간(22:30~23:30)과 겹침
        PayBlock b = work(1, "2026-11-03", t(14, 0), t(23, 30), 30, t(18, 0));
        Result r = calc(true, PART, b);
        assertEquals(5_000, r.overtimePremium());             // 1h x 0.5 x 10000
        assertEquals(7_500, r.nightPremium());                // 1.5h x 0.5 x 10000
    }

    @Test
    void weeklyOvertimeCountsOnlyHoursBeyondFortyNotAlreadyDaily() {
        List<PayBlock> six = new ArrayList<>();
        for (int d = 2; d <= 7; d++) six.add(work(1, "2026-11-0" + d, t(9, 0), t(18, 0), 60, t(13, 0))); // 6일 x 8h
        Result r = LaborCostCalculator.calculate(NOV, true, List.of(PART), six);
        assertEquals(480_000, r.basePay());
        assertEquals(40_000, r.overtimePremium());            // 6일째 8h 전부 주 40h 초과
        assertEquals(80_000, r.weeklyHolidayPay());           // 주휴는 40h 상한 -> 8h 분
        assertEquals(600_000, r.total());
    }

    @Test
    void annualLeaveIsPaid8HoursAndCountsTowardWeeklyHolidayForFullTime() {
        PayBlock mon = work(2, "2026-11-02", t(9, 0), t(18, 0), 60, t(13, 0));
        Result r = calc(false, FULL, mon, work(2, "2026-11-03", t(9, 0), t(18, 0), 60, t(13, 0)),
                work(2, "2026-11-04", t(9, 0), t(18, 0), 60, t(13, 0)),
                work(2, "2026-11-05", t(9, 0), t(18, 0), 60, t(13, 0)), leave(2, "2026-11-06"));
        assertEquals(480_000, r.basePay());                   // (4x8h + 연차 8h) x 12000
        assertEquals(96_000, r.weeklyHolidayPay());           // 40h -> 8h 분
        assertEquals(0, r.holidayPayTargetCount());           // 대상자 수는 알바생만 센다
        assertEquals(1920, r.workers().get(0).workMinutes()); // 연차는 실근로시간에 넣지 않는다
    }

    @Test
    void partTimerLeaveIsNotPaid() {
        Result r = calc(false, PART, leave(1, "2026-11-02"));
        assertEquals(0, r.basePay());
    }

    @Test
    void weekIsAttributedToTheMonthOfItsMonday() {
        // 10/26(월) 주: 10/26~28 각 4h + 11/1(일) 4h = 16h
        PayBlock[] blocks = {
                work(1, "2026-10-26", t(9, 0), t(13, 0), 0, 0), work(1, "2026-10-27", t(9, 0), t(13, 0), 0, 0),
                work(1, "2026-10-28", t(9, 0), t(13, 0), 0, 0), work(1, "2026-11-01", t(9, 0), t(13, 0), 0, 0)};

        Result nov = calc(false, PART, blocks);
        assertEquals(40_000, nov.basePay());                  // 11월 날짜(11/1)만
        assertEquals(0, nov.weeklyHolidayPay());              // 월요일이 10월이라 10월 몫
        assertEquals(0, nov.holidayPayTargetCount());

        Result oct = LaborCostCalculator.calculate(YearMonth.of(2026, 10), false, List.of(PART), List.of(blocks));
        assertEquals(120_000, oct.basePay());
        assertEquals(32_000, oct.weeklyHolidayPay());         // 16h: 10000 x 16/40 x 8, 11/1 근무도 그 주에 포함
        assertEquals(1, oct.holidayPayTargetCount());
    }

    @Test
    void weeklyOvertimeSpansMonthBoundary() {
        // 10/26~10/30 8h x 5일(=40h, 10월) 뒤 11/1(일) 4h 는 주 40h 초과 -> 11월 귀속 연장 4h
        List<PayBlock> blocks = new ArrayList<>();
        for (int d = 26; d <= 30; d++) blocks.add(work(1, "2026-10-" + d, t(9, 0), t(18, 0), 60, t(13, 0)));
        blocks.add(work(1, "2026-11-01", t(9, 0), t(13, 0), 0, 0));
        Result nov = LaborCostCalculator.calculate(NOV, true, List.of(PART), blocks);
        assertEquals(20_000, nov.overtimePremium());          // 4h x 0.5 x 10000
    }

    @Test
    void payBlocksSkipTentativeWithoutTimesAndDaysOff() {
        Worker w = new Worker();
        w.setId(1L);
        Shift ok = shift(w, Shift.Status.NORMAL, LocalTime.of(9, 0), LocalTime.of(14, 0));
        Shift tentativeNoTime = shift(w, Shift.Status.TENTATIVE, null, null);
        Shift off = shift(w, Shift.Status.DAY_OFF, null, null);
        Shift annual = shift(w, Shift.Status.ANNUAL_LEAVE, null, null);
        List<PayBlock> blocks = LaborCostCalculator.payBlocks(List.of(ok, tentativeNoTime, off, annual));
        assertEquals(2, blocks.size());
        assertTrue(blocks.get(1).leave());
    }

    private static Shift shift(Worker w, Shift.Status status, LocalTime start, LocalTime end) {
        Shift s = new Shift();
        s.setWorker(w);
        s.setStatus(status);
        s.setWorkDate(LocalDate.of(2026, 11, 2));
        s.setStartTime(start);
        s.setEndTime(end);
        s.setBreakMinutes(0);
        return s;
    }
}