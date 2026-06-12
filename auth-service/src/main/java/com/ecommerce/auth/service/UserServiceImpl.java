package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AdminUserResponse;
import com.ecommerce.auth.dto.ChangeRoleRequest;
import com.ecommerce.auth.dto.UpdateMeRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

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
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
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
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
    }

    @Override
    public AdminUserResponse changeRole(UUID userId, ChangeRoleRequest request) {
        User user = getUserOrThrow(userId);
        user.setRole(request.getRole());
        User saved = userRepository.save(user);

        refreshTokenService.revokeAllForUser(userId);

        return toAdminResponse(saved);
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private User getActiveUserOrThrow(UUID userId) {
        User user = getUserOrThrow(userId);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        return user;
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus()
        );
    }

    private AdminUserResponse toAdminResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}