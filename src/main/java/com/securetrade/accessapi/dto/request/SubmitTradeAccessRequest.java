package com.securetrade.accessapi.dto.request;

import com.securetrade.accessapi.common.enums.TradeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class SubmitTradeAccessRequest {

    @NotBlank(message = "Symbol is required")
    @Pattern(
            regexp = "^[A-Z0-9.-]{1,12}$",
            message = "Symbol format is not valid")
    private String symbol;

    @NotNull(message = "Trade type is required")
    private TradeType tradeType;

    @NotNull(message = "Requested volume is required")
    @Positive(message = "Requested volume must be positive")
    @Digits(
            integer = 13,
            fraction = 2,
            message = "Requested volume must fit 13 whole digits and 2 decimal digits")
    private BigDecimal requestedVolume;

    @NotNull(message = "Risk score is required")
    @DecimalMin(value = "0.00", message = "Risk score cannot be below 0.00")
    @DecimalMax(value = "1.00", message = "Risk score cannot be above 1.00")
    private BigDecimal riskScore;

    public SubmitTradeAccessRequest() {
    }

    public SubmitTradeAccessRequest(
            String symbol,
            TradeType tradeType,
            BigDecimal requestedVolume,
            BigDecimal riskScore) {

        this.symbol = symbol;
        this.tradeType = tradeType;
        this.requestedVolume = requestedVolume;
        this.riskScore = riskScore;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public void setTradeType(TradeType tradeType) {
        this.tradeType = tradeType;
    }

    public BigDecimal getRequestedVolume() {
        return requestedVolume;
    }

    public void setRequestedVolume(BigDecimal requestedVolume) {
        this.requestedVolume = requestedVolume;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }
}
