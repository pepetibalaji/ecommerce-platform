package com.ecommerce.auth.controller;

import com.ecommerce.auth.service.AuthOutboxRecoveryService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
public class AdminOutboxController {
  private final AuthOutboxRecoveryService recovery;
  @PostMapping("/replay-dead-letters")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Integer> replayDeadLetters() { return Map.of("replayed", recovery.replayDeadLetters()); }
}
