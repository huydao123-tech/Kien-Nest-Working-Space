package com.cowork.business.service;

import com.cowork.dataaccess.entity.Money;

public record PriceQuote(int blocks, int blockMinutes, Money total) {
}
