package com.example.moneybug.controller;

import com.example.moneybug.dto.PartDto;
import com.example.moneybug.dto.StoreDemandDto;
import com.example.moneybug.dto.StoreDto;
import com.example.moneybug.dto.StoreHoursDto;
import com.example.moneybug.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    // ---- 내 매장 ----

    @GetMapping("/stores/me")
    public StoreDto.Response get(@AuthenticationPrincipal Long userId) {
        return storeService.get(userId);
    }

    /** 매장이 없으면 만들고, 있으면 수정한다. */
    @PutMapping("/stores/me")
    public StoreDto.Response save(@AuthenticationPrincipal Long userId, @Valid @RequestBody StoreDto.Request request) {
        return storeService.save(userId, request);
    }

    // ---- 요일별 영업시간 ----

    @GetMapping("/stores/me/hours")
    public List<StoreHoursDto.Response> hours(@AuthenticationPrincipal Long userId) {
        return storeService.hours(userId);
    }

    @PutMapping("/stores/me/hours")
    public List<StoreHoursDto.Response> replaceHours(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody StoreHoursDto.ReplaceRequest request) {
        return storeService.replaceHours(userId, request);
    }

    // ---- 시간대별 필요 인원 ----

    @GetMapping("/stores/me/demand")
    public List<StoreDemandDto.Response> demand(@AuthenticationPrincipal Long userId) {
        return storeService.demand(userId);
    }

    @PutMapping("/stores/me/demand")
    public List<StoreDemandDto.Response> replaceDemand(@AuthenticationPrincipal Long userId,
                                                       @Valid @RequestBody StoreDemandDto.ReplaceRequest request) {
        return storeService.replaceDemand(userId, request);
    }

    // ---- 근무 파트 ----

    @GetMapping("/stores/me/parts")
    public List<PartDto.Response> parts(@AuthenticationPrincipal Long userId) {
        return storeService.parts(userId);
    }

    @PostMapping("/stores/me/parts")
    public ResponseEntity<PartDto.Response> createPart(@AuthenticationPrincipal Long userId,
                                                       @Valid @RequestBody PartDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeService.createPart(userId, request));
    }

    @PutMapping("/parts/{partId}")
    public PartDto.Response updatePart(@AuthenticationPrincipal Long userId, @PathVariable Long partId,
                                       @Valid @RequestBody PartDto.Request request) {
        return storeService.updatePart(userId, partId, request);
    }

    @DeleteMapping("/parts/{partId}")
    public ResponseEntity<Void> deletePart(@AuthenticationPrincipal Long userId, @PathVariable Long partId) {
        storeService.deletePart(userId, partId);
        return ResponseEntity.noContent().build();
    }
}
