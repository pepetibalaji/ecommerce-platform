package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AdminUserResponse;
import com.ecommerce.auth.dto.ReplaceRolesRequest;
import com.ecommerce.auth.dto.UpdateStatusRequest;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.service.AccountService;
import com.ecommerce.auth.service.AuditRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Admin user management APIs")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

  private static final int MAX_PAGE_SIZE = 100;

  private final AccountService userService;

  public AdminUserController(AccountService userService) {
    this.userService = userService;
  }

  @GetMapping
  @Operation(summary = "List all users")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERMISSION_USER:READ')")
  public Page<AdminUserResponse> getAllUsers(
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest servletRequest,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int pageNumber = Math.max(0, page);
    int pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    List<AdminUserResponse> users =
        userService.list(currentUserId(jwt), AuditRequestContext.from(servletRequest));
    long start = (long) pageNumber * pageSize;
    List<AdminUserResponse> content =
        start >= users.size()
            ? List.of()
            : users.subList((int) start, (int) Math.min(start + pageSize, users.size()));
    return new PageImpl<>(content, PageRequest.of(pageNumber, pageSize), users.size());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get user by id")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERMISSION_USER:READ')")
  public AdminUserResponse getUserById(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
    return userService.get(id, currentUserId(jwt), AuditRequestContext.from(servletRequest));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete user")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERMISSION_USER:STATUS_WRITE')")
  public void deleteUser(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
    userService.status(
        id,
        statusRequest(UserStatus.DELETED),
        currentUserId(jwt),
        AuditRequestContext.from(servletRequest));
  }

  @PutMapping("/{id}/roles")
  @Operation(summary = "Replace user roles")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERMISSION_USER:ROLE_WRITE')")
  public AdminUserResponse changeRole(
      @PathVariable UUID id,
      @Valid @RequestBody ReplaceRolesRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest servletRequest) {
    UUID actorUserId = currentUserId(jwt);
    AuditRequestContext context = AuditRequestContext.from(servletRequest);
    userService.roles(id, request, actorUserId, context);
    return userService.get(id, actorUserId, context);
  }

  @DeleteMapping("/{id}/sessions")
  @Operation(summary = "Revoke all user sessions")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERMISSION_USER:SESSION_REVOKE')")
  public void forceLogout(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
    userService.forceLogout(id, currentUserId(jwt), AuditRequestContext.from(servletRequest));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERMISSION_USER:STATUS_WRITE')")
  public void updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateStatusRequest request,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest servletRequest) {
    userService.status(
        id, request, currentUserId(jwt), AuditRequestContext.from(servletRequest));
  }

  private UpdateStatusRequest statusRequest(UserStatus status) {
    UpdateStatusRequest request = new UpdateStatusRequest();
    request.setStatus(status);
    return request;
  }

  private UUID currentUserId(Jwt jwt) {
    return UUID.fromString(jwt.getClaimAsString("userId"));
  }
}
