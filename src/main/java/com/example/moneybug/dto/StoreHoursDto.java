package com.example.moneybug.dto;

import com.example.moneybug.entity.StoreHours;
import com.example.moneybug.entity.Weekday;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;

public class StoreHoursDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        @NotNull
        private Weekday dayOfWeek;

        @NotNull
        private LocalTime openTime;

        @NotNull
        private LocalTime closeTime;
    }

    /** 영업시간은 요일별로 통째로 교체한다. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReplaceRequest {
        @NotNull
        @Valid
        private List<Item> hours;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Weekday dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;

        public static Response from(StoreHours hours) {
            return Response.builder()
                    .id(hours.getId())
                    .dayOfWeek(hours.getDayOfWeek())
                    .openTime(hours.getOpenTime())
                    .closeTime(hours.getCloseTime())
                    .build();
        }
    }
}
