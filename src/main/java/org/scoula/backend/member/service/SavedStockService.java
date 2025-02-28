package org.scoula.backend.member.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.scoula.backend.global.response.ApiResponse;
import org.scoula.backend.member.controller.response.SavedStockResponseDto;
import org.scoula.backend.member.domain.Company;
import org.scoula.backend.member.domain.Member;
import org.scoula.backend.member.domain.SavedStock;
import org.scoula.backend.member.exception.NotAuthorizedException;
import org.scoula.backend.member.exception.ResourceNotFoundException;
import org.scoula.backend.member.repository.CompanyJpaRepository;
import org.scoula.backend.member.repository.SavedStockJpaRepository;
import org.scoula.backend.member.repository.impls.CompanyRepositoryImpl;
import org.scoula.backend.member.repository.impls.SavedStockRepositoryImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SavedStockService {
    private final SavedStockRepositoryImpl savedStockJpaRepository;
    private final CompanyRepositoryImpl companyJpaRepository;

    /**
     * Getting list of stock saved by the member
     *
     * @param member: Member for filtering out
     * @return ON Success list of saved stocks
     */
    public ResponseEntity<ApiResponse<List<SavedStockResponseDto>>> stockGet(Member member) {

        List<SavedStock> savedStockList = savedStockJpaRepository.findAllByMember(member).orElseThrow(
                () -> new ResourceNotFoundException("유효하지않은 정보입니다.")
        );

        List<SavedStockResponseDto> savedStockResponseDtoList = savedStockList.stream()
                .filter(savedStock -> savedStock.getDeletedDateTime() == null)
                .map(savedStock -> new SavedStockResponseDto(
                        savedStock.getId(),
                        savedStock.getCompany().getIsuSrtCd(),
                        savedStock.getCompany().getIsuNm()
                ))
                .toList();

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(
                        "조회 성공",
                        savedStockResponseDtoList,
                        HttpStatus.OK.value()
                ));
    }

    /**
     * Saves a stock for the given member.
     *
     * @param member     The member who is saving the stock.
     * @param tickerCode The ticker symbol of the stock to save.
     * @return ResponseEntity containing the result message.
     */
    public ResponseEntity<ApiResponse<String>> stockSave(Member member, String tickerCode) {
        Company company = companyJpaRepository.findByIsuSrtCd(tickerCode).orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 종목코드 입니다."));

        SavedStock savedStock = SavedStock.builder()
                .member(member)
                .company(company)
                .context("N/A")
                .build();

        savedStockJpaRepository.save(savedStock);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(
                        "저장 성공.",
                        null,
                        HttpStatus.OK.value()
                ));
    }

    /**
     * Soft delete stocks
     *
     * @param member: Member for filtering out
     * @param savedStockId:
     * @return success message
     */
    @Transactional
    public ResponseEntity<ApiResponse<String>> stockDelete(Member member, Long savedStockId) {
        SavedStock savedStock = findByIdSavedStockAuthCheck(member.getId(), savedStockId);
        savedStock.softDelete(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(
                        "삭제 됐습니다.",
                        null,
                        HttpStatus.OK.value()
                ));
    }

    private SavedStock findByIdSavedStockAuthCheck(Long memberId, Long savedStockId) {
        SavedStock savedStock = savedStockJpaRepository.findById(savedStockId).orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 정보입니다."));
        if (!savedStock.getMember().getId().equals(memberId) || savedStock.isDeleted())
            throw new NotAuthorizedException("잘못된 요청입니다.");
        return savedStock;
    }
}
