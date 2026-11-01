package com.example.moneybug.dto;

import com.example.moneybug.entity.Notice;
import com.example.moneybug.entity.Store;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

public class NoticeDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotNull
        private Integer storeId;

        @NotBlank
        private String content;

        /** 연관 엔티티(매장)는 서비스에서 조회해 넘겨준다. */
        public Notice toEntity(Store store) {
            Notice notice = new Notice();
            notice.setStore(store);
            notice.setContent(content);
            return notice;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Integer storeId;
        private String content;
        private LocalDateTime createdAt;

        public static Response from(Notice notice) {
            return Response.builder()
                    .id(notice.getId())
                    .storeId(notice.getStore().getId())
                    .content(notice.getContent())
                    .createdAt(notice.getCreatedAt())
                    .build();
        }
    }
}
