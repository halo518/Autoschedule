package com.example.moneybug.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payroll_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false)
    private StoreWorker storeWorker;

    // 정산 연월 (예: 2026-10)
    @Column(name = "payroll_month", nullable = false, length = 7)
    private String payrollMonth;

    // 분 단위
    @Column(name = "total_work_time", nullable = false)
    private Integer totalWorkTime;

    @Column(name = "total_holiday_allowance")
    private Integer totalHolidayAllowance = 0;

    @Column(name = "total_overtime_allowance")
    private Integer totalOvertimeAllowance = 0;

    @Column(name = "weekly_holiday_allowance")
    private Integer weeklyHolidayAllowance = 0;

    @Column(name = "deduction_amount")
    private Integer deductionAmount = 0;

    @Column(name = "final_salary", nullable = false)
    private Integer finalSalary;
}
