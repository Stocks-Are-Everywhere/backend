package org.scoula.backend.member.controller.response;

import lombok.Getter;

@Getter
public class SavedStockResponseDto {
    private final Long savedStockId;
    private final String tickerCode;
    private final String stockName;

    public SavedStockResponseDto(Long savedStockId, String tickerCode, String stockName) {
        this.savedStockId = savedStockId;
        this.tickerCode = tickerCode;
        this.stockName = stockName;
    }
}
