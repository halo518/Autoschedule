package com.example.moneybug.repository;

import com.example.moneybug.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 MySQL 에 전체 엔티티 체인을 저장/조회한다. 테스트 종료 시 롤백된다. */
@SpringBootTest(properties = "jwt.secret=test-secret-test-secret-test-secret-12")
@Transactional
class RepositoryDbTest {

    @Autowired UserRepository userRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreWorkerRepository storeWorkerRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired SubstituteRequestRepository substituteRequestRepository;
    @Autowired PayrollRepository payrollRepository;
    @Autowired ComplianceRepository complianceRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired EntityManager em;

    private User user(String kakaoId, User.Role role) {
        User u = new User();
        u.setKakaoId(kakaoId);
        u.setRole(role);
        u.setName("테스트" + kakaoId);
        u.setContact("010-0000-0000");
        return userRepository.save(u);
    }

    @Test
    void saveAndReadAllEntities() {
        User employer = user("db-test-employer", User.Role.EMPLOYER);
        User worker = user("db-test-worker", User.Role.WORKER);
        User taker = user("db-test-taker", User.Role.WORKER);

        Store store = new Store();
        store.setEmployer(employer);
        store.setStoreName("테스트 매장");
        store = storeRepository.save(store);

        StoreWorker sw = new StoreWorker();
        sw.setStore(store);
        sw.setWorker(worker);
        sw.setHourlyWage(10320);
        sw.setTaxType(StoreWorker.TaxType.FOUR_INSURANCE);
        sw = storeWorkerRepository.save(sw);

        Schedule schedule = new Schedule();
        schedule.setStoreWorker(sw);
        schedule.setWorkDate(LocalDate.of(2026, 10, 1));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(18, 0));
        schedule = scheduleRepository.save(schedule);

        Attendance attendance = new Attendance();
        attendance.setSchedule(schedule);
        attendance.setActualStart(LocalDateTime.of(2026, 10, 1, 9, 1));
        attendance.setCheckMethod(Attendance.CheckMethod.QR);
        attendance = attendanceRepository.save(attendance);

        SubstituteRequest request = new SubstituteRequest();
        request.setSchedule(schedule);
        request.setRequester(worker);
        request.setTaker(taker);
        request.setReason("개인 사정");
        request = substituteRequestRepository.save(request);

        Payroll payroll = new Payroll();
        payroll.setStoreWorker(sw);
        payroll.setPayrollMonth("2026-10");
        payroll.setTotalWorkTime(540);
        payroll.setFinalSalary(90000);
        payroll = payrollRepository.save(payroll);

        Compliance compliance = new Compliance();
        compliance.setStoreWorker(sw);
        compliance.setHealthCertExpiry(LocalDate.of(2027, 1, 1));
        compliance = complianceRepository.save(compliance);

        Notice notice = new Notice();
        notice.setStore(store);
        notice.setContent("공지");
        notice = noticeRepository.save(notice);

        // INSERT 를 DB 로 내보내고 1차 캐시를 비워 실제 SELECT 로 다시 읽는다.
        em.flush();
        em.clear();

        assertEquals("db-test-employer", userRepository.findByKakaoId("db-test-employer").orElseThrow().getKakaoId());
        assertEquals("테스트 매장", storeRepository.findById(store.getId()).orElseThrow().getStoreName());
        assertEquals(StoreWorker.TaxType.FOUR_INSURANCE,
                storeWorkerRepository.findById(sw.getId()).orElseThrow().getTaxType());
        assertEquals(LocalTime.of(9, 0), scheduleRepository.findById(schedule.getId()).orElseThrow().getStartTime());
        assertEquals(Attendance.CheckMethod.QR,
                attendanceRepository.findById(attendance.getId()).orElseThrow().getCheckMethod());
        assertEquals(SubstituteRequest.Status.PENDING,
                substituteRequestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals("2026-10", payrollRepository.findById(payroll.getId()).orElseThrow().getPayrollMonth());
        assertEquals(LocalDate.of(2027, 1, 1),
                complianceRepository.findById(compliance.getId()).orElseThrow().getHealthCertExpiry());
        assertNotNull(noticeRepository.findById(notice.getId()).orElseThrow().getCreatedAt());
    }
}
