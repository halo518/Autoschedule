package com.example.autoschedule.service;

import java.time.LocalTime;

/** 시각 <-> "자정 기준 분" 변환. 종료 24:00 은 1440 으로 다룬다(DB 에는 00:00 으로 저장). */
public final class Minutes {

    private Minutes() {}

    public static int ofStart(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    public static int ofEnd(LocalTime start, LocalTime end) {
        if (end.equals(LocalTime.MIDNIGHT) && !(start == null || start.equals(LocalTime.MIDNIGHT))) return 1440;
        return end.getHour() * 60 + end.getMinute();
    }

    public static LocalTime toTime(int minutes) {
        return minutes >= 1440 ? LocalTime.MIDNIGHT : LocalTime.of(minutes / 60, minutes % 60);
    }

    public static String format(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}