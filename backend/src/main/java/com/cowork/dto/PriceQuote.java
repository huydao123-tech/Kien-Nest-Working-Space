package com.cowork.dto;

import com.cowork.dataaccess.entity.Money;

public record PriceQuote(int blocks, int blockMinutes, Money total) {
}
