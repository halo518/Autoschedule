package com.example.autoschedule.dto;

import java.time.LocalTime;

/** api-spec 시각 문자열("HH:MM", 24:00 허용) <-> LocalTime 변환. 24:00 은 00:00(자정)으로 저장한다. */
public final class TimeText {

    private TimeText() {}

    public static LocalTime parse(String text) {
        if (text == null || text.isBlank()) return null;
        if (text.equals("24:00")) return LocalTime.MIDNIGHT;
        return LocalTime.parse(text);
    }

    public static String format(LocalTime time) {
        return time == null ? null : String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    /** 종료 시각용: 시작보다 이른(또는 같은) 00:00 은 자정으로 보고 "24:00" 으로 내려준다. */
    public static String formatEnd(LocalTime start, LocalTime end) {
        if (end == null) return null;
        if (end.equals(LocalTime.MIDNIGHT) && (start == null || !start.equals(LocalTime.MIDNIGHT))) return "24:00";
        return format(end);
    }
}