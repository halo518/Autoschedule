package com.example.moneybug.dto;

import com.example.moneybug.entity.Attendance;
import com.example.moneybug.entity.Schedule;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

public class AttendanceDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotNull
        private Integer scheduleId;

        private LocalDateTime actualStart;
        private LocalDateTime actualEnd;
        private Integer overtimeMinutes = 0;

        @NotNull
        private Attendance.CheckMethod checkMethod;

        private Integer holidayPayRate = 0;

        /** 연관 엔티티(근무표)는 서비스에서 조회해 넘겨준다. */
        public Attendance toEntity(Schedule schedule) {
            Attendance attendance = new Attendance();
            attendance.setSchedule(schedule);
            attendance.setActualStart(actualStart);
            attendance.setActualEnd(actualEnd);
            attendance.setOvertimeMinutes(overtimeMinutes);
            attendance.setCheckMethod(checkMethod);
            attendance.setHolidayPayRate(holidayPayRate);
            return attendance;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Integer scheduleId;
        private LocalDateTime actualStart;
        private LocalDateTime actualEnd;
        private Integer overtimeMinutes;
        private Attendance.CheckMethod checkMethod;
        private Integer holidayPayRate;

        public static Response from(Attendance attendance) {
            return Response.builder()
                    .id(attendance.getId())
                    .scheduleId(attendance.getSchedule().getId())
                    .actualStart(attendance.getActualStart())
                    .actualEnd(attendance.getActualEnd())
                    .overtimeMinutes(attendance.getOvertimeMinutes())
                    .checkMethod(attendance.getCheckMethod())
                    .holidayPayRate(attendance.getHolidayPayRate())
                    .build();
        }
    }
}
