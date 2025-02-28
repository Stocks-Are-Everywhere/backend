package org.scoula.backend.member.controller;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.scoula.backend.global.response.ApiResponse;
import org.scoula.backend.global.security.UserDetailsImpl;
import org.scoula.backend.member.controller.response.SavedStockResponseDto;
import org.scoula.backend.member.service.SavedStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/saved")
public class SavedStockController {
    private final SavedStockService savedStockService;

    @GetMapping("/stock")
    public ResponseEntity<ApiResponse<List<SavedStockResponseDto>>> stockGet(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return savedStockService.stockGet(userDetails.getMember());
    }

    @PostMapping("/stock/{tickerCode}")
    public ResponseEntity<ApiResponse<String>> stockSave(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable String tickerCode) {
        return savedStockService.stockSave(userDetails.getMember(), tickerCode);
    }

    @Transactional
    @PutMapping("/stock/{savedStockId}")
    public ResponseEntity<ApiResponse<String>> stockDelete(@AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable Long savedStockId) {
        return savedStockService.stockDelete(userDetails.getMember(), savedStockId);
    }
}
