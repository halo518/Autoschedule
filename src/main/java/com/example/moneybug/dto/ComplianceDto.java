package com.example.moneybug.dto;

import com.example.moneybug.entity.Compliance;
import com.example.moneybug.entity.StoreWorker;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

public class ComplianceDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        @NotNull
        private Integer mappingId;

        private LocalDate healthCertExpiry;

        @Size(max = 255)
        private String contractDocUrl;

        /** 연관 엔티티(매장-직원 매핑)는 서비스에서 조회해 넘겨준다. */
        public Compliance toEntity(StoreWorker storeWorker) {
            Compliance compliance = new Compliance();
            compliance.setStoreWorker(storeWorker);
            compliance.setHealthCertExpiry(healthCertExpiry);
            compliance.setContractDocUrl(contractDocUrl);
            return compliance;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer id;
        private Integer mappingId;
        private LocalDate healthCertExpiry;
        private String contractDocUrl;

        public static Response from(Compliance compliance) {
            return Response.builder()
                    .id(compliance.getId())
                    .mappingId(compliance.getStoreWorker().getId())
                    .healthCertExpiry(compliance.getHealthCertExpiry())
                    .contractDocUrl(compliance.getContractDocUrl())
                    .build();
        }
    }
}
