package com.example.moneybug.dto;

import com.example.moneybug.entity.Store;
import com.example.moneybug.entity.StoreWorker;
import com.example.moneybug.entity.User;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class StoreWorkerDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotNull
        private Long storeId;

        @NotNull
        private Long workerId;

        @NotNull
        @Positive
        private Integer hourlyWage;

        private StoreWorker.TaxType taxType = StoreWorker.TaxType.NONE;

        @Size(max = 20)
        private String status = "ACTIVE";

        /** 연관 엔티티(매장, 직원)는 서비스에서 조회해 넘겨준다. */
        public StoreWorker toEntity(Store store, User worker) {
            StoreWorker storeWorker = new StoreWorker();
            storeWorker.setStore(store);
            storeWorker.setWorker(worker);
            storeWorker.setHourlyWage(hourlyWage);
            storeWorker.setTaxType(taxType);
            storeWorker.setStatus(status);
            return storeWorker;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Long storeId;
        private Long workerId;
        private Integer hourlyWage;
        private StoreWorker.TaxType taxType;
        private String status;

        public static Response from(StoreWorker storeWorker) {
            return Response.builder()
                    .id(storeWorker.getId())
                    .storeId(storeWorker.getStore().getId())
                    .workerId(storeWorker.getWorker().getId())
                    .hourlyWage(storeWorker.getHourlyWage())
                    .taxType(storeWorker.getTaxType())
                    .status(storeWorker.getStatus())
                    .build();
        }
    }
}
