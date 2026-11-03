package com.example.moneybug.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
public class Worker {

    /** FULL_TIME = 직원(유동 근무), PART_TIME = 알바생(파트 근무) */
    public enum EmploymentType { FULL_TIME, PART_TIME }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false)
    private EmploymentType employmentType;

    @Column(length = 50)
    private String title;

    @Column(name = "hourly_wage", nullable = false)
    private Integer hourlyWage;

    @Column(length = 20)
    private String phone;

    @Column(name = "is_minor")
    private boolean minor;

    @Column(name = "contract_weekly_hours")
    private Integer contractWeeklyHours;

    @Column(name = "min_weekly_hours")
    private Integer minWeeklyHours;

    @Column(name = "max_weekly_hours")
    private Integer maxWeeklyHours;
}
