package com.ecommerce.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.auth.config.BrowserSessionProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BrowserRefreshCookieServiceTest {

  @Test
  void writesAndClearsHttpOnlyRefreshCookieWithoutExposingItToJavaScript() {
    BrowserSessionProperties properties = new BrowserSessionProperties();
    BrowserRefreshCookieService service = new BrowserRefreshCookieService(properties);
    MockHttpServletResponse response = new MockHttpServletResponse();

    service.write(response, "opaque-refresh-secret");

    String header = response.getHeader("Set-Cookie");
    assertThat(header).contains("pepekart_refresh=opaque-refresh-secret", "HttpOnly", "SameSite=Lax", "Path=/api/v1/auth");
    assertThat(header).doesNotContain("Domain=");

    service.clear(response);
    assertThat(response.getHeaders("Set-Cookie").get(1)).contains("Max-Age=0", "HttpOnly");
  }

  @Test
  void readsOnlyTheConfiguredRefreshCookie() {
    BrowserSessionProperties properties = new BrowserSessionProperties();
    BrowserRefreshCookieService service = new BrowserRefreshCookieService(properties);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("unrelated", "ignore"), new Cookie("pepekart_refresh", "opaque-secret"));

    assertThat(service.read(request)).contains("opaque-secret");
  }
}
