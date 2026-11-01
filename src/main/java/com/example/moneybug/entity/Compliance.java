package com.example.moneybug.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "compliance")
@Getter
@Setter
@NoArgsConstructor
public class Compliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compliance_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false)
    private StoreWorker storeWorker;

    @Column(name = "health_cert_expiry")
    private LocalDate healthCertExpiry;

    @Column(name = "contract_doc_url")
    private String contractDocUrl;
}
