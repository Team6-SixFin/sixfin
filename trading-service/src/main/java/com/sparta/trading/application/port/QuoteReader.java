package com.sparta.trading.application.port;

import java.util.List;

public interface QuoteReader {
    Quote read(String symbol);
    List<Quote> readAll(List<String> symbolList);
}
