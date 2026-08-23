package com.securetrade.accessapi.dto.request;

import com.securetrade.accessapi.common.enums.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Trade request to evaluate")
public class SubmitTradeAccessRequest {

    @Schema(
            description = "Market symbol in upper-case format",
            example = "AAPL",
            maxLength = 12)
    @NotBlank(message = "Symbol is required")
    @Pattern(
            regexp = "^[A-Z0-9.-]{1,12}$",
            message = "Symbol format is not valid")
    private String symbol;

    @Schema(description = "Trade direction", example = "BUY", allowableValues = {"BUY", "SELL"})
    @NotNull(message = "Trade type is required")
    private TradeType tradeType;

    @Schema(description = "Requested trade volume", example = "500000.00")
    @NotNull(message = "Requested volume is required")
    @Positive(message = "Requested volume must be positive")
    @Digits(
            integer = 13,
            fraction = 2,
            message = "Requested volume must fit 13 whole digits and 2 decimal digits")
    private BigDecimal requestedVolume;

    @Schema(
            description = "Risk score from 0.00 to 1.00",
            example = "0.20",
            minimum = "0.00",
            maximum = "1.00")
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
