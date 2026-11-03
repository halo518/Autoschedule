package com.example.moneybug.dto;

import com.example.moneybug.entity.StoreDemand;
import com.example.moneybug.entity.Weekday;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;

public class StoreDemandDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        @NotNull
        private Weekday dayOfWeek;

        @NotNull
        private LocalTime startTime;

        @NotNull
        private LocalTime endTime;

        @NotNull
        @Min(0)
        private Integer requiredCount;
    }

    /** 시간대별 필요 인원은 통째로 교체한다. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReplaceRequest {
        @NotNull
        @Valid
        private List<Item> demand;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Weekday dayOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer requiredCount;

        public static Response from(StoreDemand demand) {
            return Response.builder()
                    .id(demand.getId())
                    .dayOfWeek(demand.getDayOfWeek())
                    .startTime(demand.getStartTime())
                    .endTime(demand.getEndTime())
                    .requiredCount(demand.getRequiredCount())
                    .build();
        }
    }
}
