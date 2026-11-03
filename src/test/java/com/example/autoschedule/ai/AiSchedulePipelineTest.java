package com.example.autoschedule.ai;

import com.example.autoschedule.ai.AiContext.*;
import com.example.autoschedule.ai.ScheduleAiClient.Turn;
import com.example.autoschedule.entity.Schedule;
import com.example.autoschedule.entity.Store;
import com.example.autoschedule.entity.Weekday;
import com.example.autoschedule.exception.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AiSchedulePipelineTest {

    /** 호출마다 미리 정해 둔 응답을 차례로 돌려주고, 받은 turns 를 기록한다. */
    static class FakeClient implements ScheduleAiClient {
        final Deque<String> responses = new ArrayDeque<>();
        final List<List<Turn>> seen = new ArrayList<>();

        FakeClient(String... r) {
            responses.addAll(List.of(r));
        }

        @Override
        public String generate(String systemPrompt, List<Turn> turns, Map<String, Object> responseSchema) {
            seen.add(List.copyOf(turns));
            return responses.removeFirst();
        }
    }

    // 2026-11: 11/2 = 월요일. 알바생 #2(성인), #3(연소자). 직원 #1.
    private static AiContext ctx() {
        Map<Weekday, List<Long>> adultParts = new EnumMap<>(Weekday.class);
        adultParts.put(Weekday.MON, List.of(10L, 11L));
        Map<Weekday, List<Long>> minorParts = new EnumMap<>(Weekday.class);
        minorParts.put(Weekday.MON, List.of(10L, 11L));

        Map<Long, StaffInfo> staff = new LinkedHashMap<>();
        staff.put(1L, new StaffInfo(1, "박매니저", true, false, 12000, "매니저", 40, null, null,
                Map.of(), new EnumMap<>(Weekday.class)));
        staff.put(2L, new StaffInfo(2, "이알바", false, false, 10320, "알바", null, 10, 20, adultParts, Map.of()));
        staff.put(3L, new StaffInfo(3, "최학생", false, true, 10320, "알바", null, 5, 10, minorParts, Map.of()));

        Map<Long, PartInfo> parts = new LinkedHashMap<>();
        parts.put(10L, new PartInfo(10, "오픈", 8 * 60, 14 * 60, 0));
        parts.put(11L, new PartInfo(11, "마감", 16 * 60, 22 * 60 + 30, 0)); // 22:30 까지 — 연소자 불가

        Map<Weekday, DayHours> hours = new EnumMap<>(Weekday.class);
        for (Weekday d : Weekday.values()) hours.put(d, new DayHours(8 * 60, 23 * 60));

        return new AiContext(1L, Store.Scale.UNDER_5, YearMonth.of(2026, 11), staff, parts, hours,
                List.of(new DemandInfo(Weekday.MON, 8 * 60, 14 * 60, 2)),
                Schedule.WeeklyHolidayPay.MINIMIZE, Schedule.Priority.FAIRNESS, true);
    }

    private static AiSchedulePipeline pipeline(ScheduleAiClient c) {
        return new AiSchedulePipeline(c, JsonMapper.builder().build());
    }

    private static String shift(long staff, String date, String start, String end, String part) {
        return "{\"staffId\":" + staff + ",\"date\":\"" + date + "\",\"start\":\"" + start + "\",\"end\":\"" + end
                + "\",\"partId\":" + (part == null ? "null" : "\"" + part + "\"") + "}";
    }

    private static String body(String notes, String... shifts) {
        return "{\"shifts\":[" + String.join(",", shifts) + "],\"unfilled\":[],\"notes\":\"" + notes + "\"}";
    }

    @Test
    void cleanResponseNeedsOneCall() {
        FakeClient client = new FakeClient(body("ok",
                shift(1, "2026-11-02", "09:07", "18:00", null),   // 15분 단위로 보정
                shift(2, "2026-11-02", "08:00", "14:00", "10")));
        var result = pipeline(client).run(ctx());

        assertEquals(1, client.seen.size());
        assertEquals(0, result.corrections());
        assertEquals(2, result.lines().size());
        assertEquals(9 * 60, result.lines().get(0).startMin()); // 09:07 -> 09:00
        assertEquals(0, result.removedCount());
    }

    @Test
    void violationTriggersRetryWithReasonAndSecondAnswerIsAccepted() {
        FakeClient client = new FakeClient(
                body("bad", shift(3, "2026-11-02", "16:00", "22:30", "11")),   // 연소자 22시 이후
                body("good", shift(3, "2026-11-02", "08:00", "14:00", "10")));
        var result = pipeline(client).run(ctx());

        assertEquals(2, client.seen.size());
        assertEquals(1, result.corrections());
        assertEquals(1, result.lines().size());
        assertEquals(0, result.removedCount());

        // 재요청에는 직전 응답과 위반 사유가 들어가며, 이름은 노출되지 않는다.
        List<Turn> second = client.seen.get(1);
        assertEquals(3, second.size());
        assertEquals("model", second.get(1).role());
        String correction = second.get(2).text();
        assertTrue(correction.contains("22:00 이후"), correction);
        assertTrue(correction.contains("#3"), correction);
        assertFalse(correction.contains("최학생"));
        assertFalse(second.get(0).text().contains("이알바"), "입력 JSON 에 이름이 들어가면 안 된다");
    }

    @Test
    void stopsAfterTwoCorrectionsAndDropsViolatingLines() {
        String bad = shift(3, "2026-11-02", "16:00", "22:30", "11");
        String fine = shift(2, "2026-11-02", "08:00", "14:00", "10");
        FakeClient client = new FakeClient(body("1", bad, fine), body("2", bad, fine), body("3", bad, fine));
        var result = pipeline(client).run(ctx());

        assertEquals(3, client.seen.size());                       // 최초 1 + 보정 2
        assertEquals(AiSchedulePipeline.MAX_CORRECTIONS, result.corrections());
        assertEquals(1, result.lines().size());                    // 위반 줄만 제거
        assertEquals(2L, result.lines().get(0).staffId());
        assertEquals(1, result.removedCount());
    }

    @Test
    void ruleChecksCatchBadLines() {
        // 존재하지 않는 직원, 영업시간 밖(직원 자유 시간), 하루 2건, 알바생 근무 불가 요일
        String resp = body("x",
                shift(99, "2026-11-02", "09:00", "12:00", null),
                shift(1, "2026-11-02", "06:00", "10:00", null),
                shift(2, "2026-11-02", "08:00", "14:00", "10"),
                shift(2, "2026-11-02", "08:00", "14:00", "10"),
                shift(2, "2026-11-03", "08:00", "14:00", "10"));
        FakeClient client = new FakeClient(resp, resp, resp);
        var result = pipeline(client).run(ctx());

        assertEquals(1, result.lines().size());
        assertEquals(4, result.removedCount());
    }

    @Test
    void malformedJsonIsRequestedOnceMoreWithoutCountingAsCorrection() {
        FakeClient client = new FakeClient("not json at all",
                body("ok", shift(2, "2026-11-02", "08:00", "14:00", "10")));
        var result = pipeline(client).run(ctx());

        assertEquals(2, client.seen.size());
        assertEquals(0, result.corrections());
        assertEquals(1, result.lines().size());
    }

    @Test
    void twoMalformedResponsesFail() {
        FakeClient client = new FakeClient("nope", "still nope");
        assertThrows(ApiException.class, () -> pipeline(client).run(ctx()));
    }

    @Test
    void infeasibleSlotsAreReportedInInput() {
        // MON 08:00~14:00 필요 2명, 가능자: 직원(시간 자유) + 알바생 #2,#3 (오픈 파트) = 3명이라 부족 없음
        assertTrue(AiPromptBuilder.infeasibleSlots(ctx()).isEmpty());

        AiContext tight = new AiContext(1L, Store.Scale.UNDER_5, YearMonth.of(2026, 11), ctx().staff(), ctx().parts(),
                ctx().hours(), List.of(new DemandInfo(Weekday.TUE, 8 * 60, 14 * 60, 2)),
                Schedule.WeeklyHolidayPay.ALLOW, Schedule.Priority.COST_SAVING, false);
        var slots = AiPromptBuilder.infeasibleSlots(tight); // 화요일은 알바생 가능 파트 없음 -> 직원 1명만
        assertEquals(1, slots.size());
        assertEquals(1, slots.get(0).shortage());
    }
}