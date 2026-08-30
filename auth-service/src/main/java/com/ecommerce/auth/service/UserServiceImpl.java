package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AdminUserResponse;
import com.ecommerce.auth.dto.ChangeRoleRequest;
import com.ecommerce.auth.dto.UpdateMeRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.kafka.UserContactEventPublisher;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserContactEventPublisher userContactEventPublisher;

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getMe(UUID userId) {
    User user = getActiveUserOrThrow(userId);
    return toProfileResponse(user);
  }

  @Override
  public UserProfileResponse updateMe(UUID userId, UpdateMeRequest request) {
    User user = getActiveUserOrThrow(userId);
    user.setName(request.getName().trim());
    return toProfileResponse(userRepository.save(user));
  }

  @Override
    public void deleteMe(UUID userId) {
        User user = getActiveUserOrThrow(userId);
        user.setStatus(UserStatus.DELETED);
        userContactEventPublisher.publish(invalidateSessions(user));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AdminUserResponse> getAllUsers(Pageable pageable) {
    return userRepository.findAll(pageable).map(this::toAdminResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public AdminUserResponse getUserById(UUID userId) {
    User user = getUserOrThrow(userId);
    return toAdminResponse(user);
  }

  @Override
    public void deleteUser(UUID userId) {
        User user = getUserOrThrow(userId);
        user.setStatus(UserStatus.DELETED);
        userContactEventPublisher.publish(invalidateSessions(user));
  }

  @Override
  public AdminUserResponse changeRole(UUID userId, ChangeRoleRequest request) {
    User user = getUserOrThrow(userId);
    user.setRole(request.getRole());
    User saved = invalidateSessions(user);
    return toAdminResponse(saved);
  }

  @Override
  public void forceLogout(UUID userId) {
    User user = getUserOrThrow(userId);
    invalidateSessions(user);
  }

  private User invalidateSessions(User user) {
    user.setTokenVersion(nextTokenVersion(user));
    User saved = userRepository.save(user);
    refreshTokenService.revokeAllForUser(saved.getId());
    return saved;
  }

  private long nextTokenVersion(User user) {
    return user.getTokenVersion() == null ? 1L : user.getTokenVersion() + 1L;
  }

  private User getUserOrThrow(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
  }

  private User getActiveUserOrThrow(UUID userId) {
    User user = getUserOrThrow(userId);
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new ResourceNotFoundException("User not found: " + userId);
    }
    return user;
  }

  private UserProfileResponse toProfileResponse(User user) {
    return new UserProfileResponse(
        user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getStatus());
  }

  private AdminUserResponse toAdminResponse(User user) {
    return new AdminUserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getRole(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
