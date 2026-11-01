package com.example.moneybug.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "store_worker")
@Getter
@Setter
@NoArgsConstructor
public class StoreWorker {

    // DB 값 '4_INSURANCE' 는 자바 식별자로 쓸 수 없어 변환기로 매핑한다.
    public enum TaxType {
        BUSINESS_TAX("BUSINESS_TAX"), FOUR_INSURANCE("4_INSURANCE"), NONE("NONE");

        private final String dbValue;

        TaxType(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }
    }

    @Converter
    public static class TaxTypeConverter implements AttributeConverter<TaxType, String> {
        @Override
        public String convertToDatabaseColumn(TaxType attribute) {
            return attribute == null ? null : attribute.getDbValue();
        }

        @Override
        public TaxType convertToEntityAttribute(String dbData) {
            if (dbData == null) return null;
            for (TaxType t : TaxType.values()) {
                if (t.getDbValue().equals(dbData)) return t;
            }
            throw new IllegalArgumentException("Unknown tax_type: " + dbData);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @Column(name = "hourly_wage", nullable = false)
    private Integer hourlyWage;

    @Convert(converter = TaxTypeConverter.class)
    @Column(name = "tax_type", nullable = false, length = 20)
    private TaxType taxType = TaxType.NONE;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
