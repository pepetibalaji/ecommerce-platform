package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AdminUserResponse;
import com.ecommerce.auth.dto.ChangeRoleRequest;
import com.ecommerce.auth.dto.UpdateMeRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(USER_ID)
                .name("Test User")
                .email("test@example.com")
                .password("encoded-password")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldGetMe() {

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        UserProfileResponse result =
                userService.getMe(USER_ID);

        assertThat(result.getId())
                .isEqualTo(USER_ID);

        assertThat(result.getName())
                .isEqualTo("Test User");

        assertThat(result.getEmail())
                .isEqualTo("test@example.com");

        assertThat(result.getRole())
                .isEqualTo(Role.CUSTOMER);

        assertThat(result.getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldThrowWhenGetMeUserNotFound() {

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getMe(USER_ID)
        );
    }

    @Test
    void shouldThrowWhenGetMeUserNotActive() {

        user.setStatus(UserStatus.DELETED);

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getMe(USER_ID)
        );
    }

    @Test
    void shouldUpdateMe() {

        UpdateMeRequest request =
                new UpdateMeRequest();

        request.setName(" Updated User ");

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(user);

        UserProfileResponse result =
                userService.updateMe(USER_ID, request);

        assertThat(user.getName())
                .isEqualTo("Updated User");

        assertThat(result.getName())
                .isEqualTo("Updated User");

        verify(userRepository)
                .save(user);
    }

    @Test
    void shouldDeleteMeAndInvalidateSessions() {

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(user);

        userService.deleteMe(USER_ID);

        assertThat(user.getStatus())
                .isEqualTo(UserStatus.DELETED);

        assertThat(user.getTokenVersion())
                .isEqualTo(1L);

        verify(userRepository)
                .save(user);

        verify(refreshTokenService)
                .revokeAllForUser(USER_ID);
    }

    @Test
    void shouldGetAllUsers() {

        PageRequest pageable =
                PageRequest.of(0, 20);

        Page<User> page =
                new PageImpl<>(List.of(user));

        when(userRepository.findAll(pageable))
                .thenReturn(page);

        Page<AdminUserResponse> result =
                userService.getAllUsers(pageable);

        assertThat(result.getTotalElements())
                .isEqualTo(1);

        assertThat(result.getContent().get(0).getId())
                .isEqualTo(USER_ID);

        assertThat(result.getContent().get(0).getEmail())
                .isEqualTo("test@example.com");
    }

    @Test
    void shouldGetUserById() {

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        AdminUserResponse result =
                userService.getUserById(USER_ID);

        assertThat(result.getId())
                .isEqualTo(USER_ID);

        assertThat(result.getEmail())
                .isEqualTo("test@example.com");

        assertThat(result.getRole())
                .isEqualTo(Role.CUSTOMER);
    }

    @Test
    void shouldThrowWhenAdminUserNotFound() {

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserById(USER_ID)
        );
    }

    @Test
    void shouldDeleteUserAndInvalidateSessions() {

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(user);

        userService.deleteUser(USER_ID);

        assertThat(user.getStatus())
                .isEqualTo(UserStatus.DELETED);

        assertThat(user.getTokenVersion())
                .isEqualTo(1L);

        verify(userRepository)
                .save(user);

        verify(refreshTokenService)
                .revokeAllForUser(USER_ID);
    }

    @Test
    void shouldChangeRoleAndInvalidateSessions() {

        ChangeRoleRequest request =
                new ChangeRoleRequest();

        request.setRole(Role.ADMIN);

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(user);

        AdminUserResponse result =
                userService.changeRole(USER_ID, request);

        assertThat(user.getRole())
                .isEqualTo(Role.ADMIN);

        assertThat(user.getTokenVersion())
                .isEqualTo(1L);

        assertThat(result.getRole())
                .isEqualTo(Role.ADMIN);

        verify(userRepository)
                .save(user);

        verify(refreshTokenService)
                .revokeAllForUser(USER_ID);
    }

    @Test
    void shouldForceLogoutUser() {

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(user);

        userService.forceLogout(USER_ID);

        assertThat(user.getTokenVersion())
                .isEqualTo(1L);

        verify(userRepository)
                .save(user);

        verify(refreshTokenService)
                .revokeAllForUser(USER_ID);
    }

    @Test
    void shouldSetTokenVersionToOneWhenCurrentVersionIsNull() {

        user.setTokenVersion(null);

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(user);

        userService.forceLogout(USER_ID);

        assertThat(user.getTokenVersion())
                .isEqualTo(1L);

        verify(refreshTokenService)
                .revokeAllForUser(USER_ID);
    }

    @Test
    void shouldNotInvalidateSessionsWhenUserNotFound() {

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.forceLogout(USER_ID)
        );

        verify(userRepository, never())
                .save(any(User.class));

        verify(refreshTokenService, never())
                .revokeAllForUser(USER_ID);
    }
}