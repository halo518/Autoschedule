package com.example.moneybug.dto;

import com.example.moneybug.entity.Part;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

public class PartDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotBlank
        @Size(max = 50)
        private String name;

        @NotNull
        private LocalTime startTime;

        @NotNull
        private LocalTime endTime;

        /** 생략하면 30분. */
        @Min(0)
        private Integer breakMinutes;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String name;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer breakMinutes;

        public static Response from(Part part) {
            return Response.builder()
                    .id(part.getId())
                    .name(part.getName())
                    .startTime(part.getStartTime())
                    .endTime(part.getEndTime())
                    .breakMinutes(part.getBreakMinutes())
                    .build();
        }
    }
}
