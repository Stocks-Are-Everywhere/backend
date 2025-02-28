package org.scoula.backend.member.controller;

import java.util.List;

import org.scoula.backend.global.response.ApiResponse;
import org.scoula.backend.global.security.UserDetailsImpl;
import org.scoula.backend.member.controller.response.SavedStockResponseDto;
import org.scoula.backend.member.service.SavedStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/saved")
public class SavedStockController {
	private final SavedStockService savedStockService;

	@GetMapping("/stock")
	public ResponseEntity<ApiResponse<List<SavedStockResponseDto>>> stockGet(
			@AuthenticationPrincipal UserDetailsImpl userDetails) {
		return savedStockService.stockGet(userDetails.getMember());
	}

	@PostMapping("/stock/{tickerCode}")
	public ResponseEntity<ApiResponse<String>> stockSave(@AuthenticationPrincipal UserDetailsImpl userDetails,
			@PathVariable(name = "tickerCode") String tickerCode) {
		return savedStockService.stockSave(userDetails.getMember(), tickerCode);
	}

	@Transactional
	@PutMapping("/stock/{savedStockId}")
	public ResponseEntity<ApiResponse<String>> stockDelete(@AuthenticationPrincipal UserDetailsImpl userDetails,
			@PathVariable(name = "tickerCode") Long savedStockId) {
		return savedStockService.stockDelete(userDetails.getMember(), savedStockId);
	}
}
