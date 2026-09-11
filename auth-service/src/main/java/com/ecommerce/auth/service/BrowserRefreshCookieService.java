package com.ecommerce.auth.service;

import com.ecommerce.auth.config.BrowserSessionProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** Keeps opaque refresh secrets out of JavaScript and response JSON. */
@Service
@RequiredArgsConstructor
public class BrowserRefreshCookieService {

  private final BrowserSessionProperties properties;

  public Optional<String> read(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return Optional.empty();
    return Arrays.stream(cookies)
        .filter(cookie -> properties.getRefreshCookie().getName().equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }

  public void write(HttpServletResponse response, String refreshToken) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshToken, properties.getRefreshCookie().getMaxAge()).toString());
  }

  public void clear(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
  }

  private ResponseCookie cookie(String value, Duration maxAge) {
    BrowserSessionProperties.RefreshCookie cookie = properties.getRefreshCookie();
    return ResponseCookie.from(cookie.getName(), value)
        .httpOnly(true)
        .secure(cookie.isSecure())
        .sameSite(cookie.getSameSite())
        .path(cookie.getPath())
        .maxAge(maxAge)
        .build();
  }
}
