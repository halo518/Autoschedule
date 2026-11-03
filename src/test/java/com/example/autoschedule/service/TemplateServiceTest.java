package com.example.autoschedule.service;

import com.example.autoschedule.dto.TemplateDto.Body;
import com.example.autoschedule.dto.TemplateDto.LeaveItem;
import com.example.autoschedule.dto.TemplateDto.ShiftItem;
import com.example.autoschedule.entity.Part;
import com.example.autoschedule.entity.Shift;
import com.example.autoschedule.entity.Worker;
import com.example.autoschedule.service.TemplateService.Placement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateServiceTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);

    private static Shift shift(long workerId, Part part, String date, String start, String end, int brk, Shift.Status st) {
        Worker w = new Worker();
        w.setId(workerId);
        Shift s = new Shift();
        s.setWorker(w);
        s.setPart(part);
        s.setStatus(st);
        s.setWorkDate(LocalDate.parse(date));
        if (start != null) {
            s.setStartTime(LocalTime.parse(start));
            s.setEndTime(LocalTime.parse(end));
        }
        s.setBreakMinutes(brk);
        s.setBreakStartTime(brk > 0 ? LocalTime.parse(start).plusHours(4) : null);
        return s;
    }

    @Test
    void firstFullWeek() {
        assertEquals(LocalDate.of(2026, 11, 2), TemplateService.firstFullWeek(NOV));
        assertEquals(LocalDate.of(2026, 12, 7), TemplateService.firstFullWeek(YearMonth.of(2026, 12)));
        assertEquals(LocalDate.of(2027, 2, 1), TemplateService.firstFullWeek(YearMonth.of(2027, 2)));
        assertFalse(TemplateService.isFullWeekIn(LocalDate.of(2026, 10, 26), NOV));
        assertFalse(TemplateService.isFullWeekIn(LocalDate.of(2026, 11, 30), NOV));
    }

    @Test
    void weekIsConvertedToDayIndexAndOutsideShiftsAreLeftOut() {
        Part open = new Part();
        open.setId(7L);
        List<Shift> shifts = List.of(
                shift(2, open, "2026-11-03", "08:00", "14:00", 30, Shift.Status.NORMAL),   // 화
                shift(1, null, "2026-11-08", "09:00", "18:00", 60, Shift.Status.TENTATIVE), // 일
                shift(1, null, "2026-11-04", null, null, 0, Shift.Status.ANNUAL_LEAVE),     // 수 연차
                shift(2, open, "2026-11-10", "08:00", "14:00", 30, Shift.Status.NORMAL));   // 다음 주 - 제외

        Body body = TemplateService.toBody(shifts, LocalDate.of(2026, 11, 2));

        assertEquals(2, body.shifts().size());
        ShiftItem tue = body.shifts().get(0);
        assertEquals(1, tue.dayIndex());
        assertEquals("7", tue.partId());
        assertEquals("08:00", tue.start());
        assertEquals("14:00", tue.end());
        assertEquals(30, tue.breakMinutes());
        assertFalse(tue.tentative());
        assertEquals(6, body.shifts().get(1).dayIndex());
        assertTrue(body.shifts().get(1).tentative());
        assertEquals(1, body.leaves().size());
        assertEquals(2, body.leaves().get(0).dayIndex());
    }

    @Test
    void bodySurvivesJsonRoundTrip() {
        Body body = new Body(List.of(new ShiftItem(0, 2L, "08:00", "24:00", "7", "11:00", 30, false)),
                List.of(new LeaveItem(2, 1L, "ANNUAL")));
        JsonMapper mapper = JsonMapper.builder().build();
        assertEquals(body, mapper.readValue(mapper.writeValueAsString(body), Body.class));
    }

    @Test
    void monthApplyCopiesShiftsOnEverySameWeekdayAndLeaveOnlyOnFirst() {
        Body body = new Body(List.of(new ShiftItem(0, 2L, "08:00", "14:00", "7", null, 0, false)), // 월
                List.of(new LeaveItem(2, 1L, "ANNUAL")));                                             // 수
        List<Placement> ps = TemplateService.monthPlacements(NOV, body);

        List<LocalDate> mondays = ps.stream().filter(p -> p.shift() != null).map(Placement::date).toList();
        assertEquals(List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 30)), mondays);
        List<LocalDate> leaves = ps.stream().filter(p -> p.leave() != null).map(Placement::date).toList();
        assertEquals(List.of(LocalDate.of(2026, 11, 4)), leaves);
    }

    @Test
    void weekApplyOnlyPlacesDatesInsideTheScheduleMonth() {
        Body body = new Body(List.of(new ShiftItem(0, 2L, "08:00", "14:00", null, null, 0, false),   // 월 = 10/26
                new ShiftItem(6, 2L, "08:00", "14:00", null, null, 0, false)),                          // 일 = 11/1
                List.of(new LeaveItem(2, 1L, "ANNUAL")));                                                // 수 = 10/28
        List<Placement> ps = TemplateService.weekPlacements(LocalDate.of(2026, 10, 26), NOV, body);

        assertEquals(1, ps.size());
        assertEquals(LocalDate.of(2026, 11, 1), ps.get(0).date());
    }
}