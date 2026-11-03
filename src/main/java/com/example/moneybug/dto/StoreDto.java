package com.example.moneybug.dto;

import com.example.moneybug.entity.Store;
import com.example.moneybug.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class StoreDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotNull
        private Long employerId;

        @NotBlank
        @Size(max = 100)
        private String storeName;

        private boolean underFive = true;

        /** 연관 엔티티(고용주)는 서비스에서 조회해 넘겨준다. */
        public Store toEntity(User employer) {
            Store store = new Store();
            store.setEmployer(employer);
            store.setStoreName(storeName);
            store.setUnderFive(underFive);
            return store;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Long employerId;
        private String storeName;
        private boolean underFive;

        public static Response from(Store store) {
            return Response.builder()
                    .id(store.getId())
                    .employerId(store.getEmployer().getId())
                    .storeName(store.getStoreName())
                    .underFive(store.isUnderFive())
                    .build();
        }
    }
}
