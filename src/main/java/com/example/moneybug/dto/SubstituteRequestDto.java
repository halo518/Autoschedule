package com.example.moneybug.dto;

import com.example.moneybug.entity.Schedule;
import com.example.moneybug.entity.SubstituteRequest;
import com.example.moneybug.entity.User;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class SubstituteRequestDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotNull
        private Integer scheduleId;

        @NotNull
        private Integer requesterId;

        private String reason;

        /** 연관 엔티티(근무표, 신청자)는 서비스에서 조회해 넘겨준다. 수락자(taker)는 수락 시 지정한다. */
        public SubstituteRequest toEntity(Schedule schedule, User requester) {
            SubstituteRequest request = new SubstituteRequest();
            request.setSchedule(schedule);
            request.setRequester(requester);
            request.setReason(reason);
            return request;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Integer scheduleId;
        private Integer requesterId;
        private Integer takerId;
        private String reason;
        private SubstituteRequest.Status status;

        public static Response from(SubstituteRequest request) {
            return Response.builder()
                    .id(request.getId())
                    .scheduleId(request.getSchedule().getId())
                    .requesterId(request.getRequester().getId())
                    .takerId(request.getTaker() == null ? null : request.getTaker().getId())
                    .reason(request.getReason())
                    .status(request.getStatus())
                    .build();
        }
    }
}
