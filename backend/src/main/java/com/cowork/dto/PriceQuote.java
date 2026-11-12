package com.cowork.dto;

import com.cowork.dataaccess.valueobject.Money;

public record PriceQuote(int blocks, int blockMinutes, Money total) {
}
