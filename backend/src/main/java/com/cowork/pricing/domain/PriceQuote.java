package com.cowork.pricing.domain;

import com.cowork.shared.domain.Money;

public record PriceQuote(int blocks, int blockMinutes, Money total) {
}
