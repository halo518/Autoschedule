package com.example.moneybug.dto;

import com.example.moneybug.entity.Schedule;
import com.example.moneybug.entity.StoreWorker;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

public class ScheduleDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotNull
        private Integer mappingId;

        @NotNull
        private LocalDate workDate;

        @NotNull
        private LocalTime startTime;

        @NotNull
        private LocalTime endTime;

        private boolean substitute = false;

        /** 연관 엔티티(매장-직원 매핑)는 서비스에서 조회해 넘겨준다. */
        public Schedule toEntity(StoreWorker storeWorker) {
            Schedule schedule = new Schedule();
            schedule.setStoreWorker(storeWorker);
            schedule.setWorkDate(workDate);
            schedule.setStartTime(startTime);
            schedule.setEndTime(endTime);
            schedule.setSubstitute(substitute);
            return schedule;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Integer mappingId;
        private LocalDate workDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean substitute;

        public static Response from(Schedule schedule) {
            return Response.builder()
                    .id(schedule.getId())
                    .mappingId(schedule.getStoreWorker().getId())
                    .workDate(schedule.getWorkDate())
                    .startTime(schedule.getStartTime())
                    .endTime(schedule.getEndTime())
                    .substitute(schedule.isSubstitute())
                    .build();
        }
    }
}
