package com.example.moneybug.dto;

import com.example.moneybug.entity.Store;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 100)
        private String branch;

        @NotNull
        private Store.Scale scale;

        @NotNull
        @Positive
        private Integer minimumWage;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String name;
        private String branch;
        private Store.Scale scale;
        private Integer minimumWage;

        public static Response from(Store store) {
            return Response.builder()
                    .id(store.getId())
                    .name(store.getName())
                    .branch(store.getBranch())
                    .scale(store.getScale())
                    .minimumWage(store.getMinimumWage())
                    .build();
        }
    }
}
