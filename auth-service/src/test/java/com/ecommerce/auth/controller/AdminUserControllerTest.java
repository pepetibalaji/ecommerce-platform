package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AdminUserResponse;
import com.ecommerce.auth.dto.ChangeRoleRequest;
import com.ecommerce.auth.service.UserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserController adminUserController;

    @Test
    void shouldGetAllUsers() {

        AdminUserResponse userResponse =
                mock(AdminUserResponse.class);

        Page<AdminUserResponse> page =
                new PageImpl<>(List.of(userResponse));

        when(userService.getAllUsers(PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AdminUserResponse> result =
                adminUserController.getAllUsers(0, 20);

        assertThat(result.getTotalElements())
                .isEqualTo(1);

        verify(userService)
                .getAllUsers(PageRequest.of(0, 20));
    }

    @Test
    void shouldGetUserById() {

        AdminUserResponse response =
                mock(AdminUserResponse.class);

        when(userService.getUserById(USER_ID))
                .thenReturn(response);

        AdminUserResponse result =
                adminUserController.getUserById(USER_ID);

        assertThat(result)
                .isSameAs(response);

        verify(userService)
                .getUserById(USER_ID);
    }

    @Test
    void shouldDeleteUser() {

        adminUserController.deleteUser(USER_ID);

        verify(userService)
                .deleteUser(USER_ID);
    }

    @Test
    void shouldChangeRole() {

        ChangeRoleRequest request =
                new ChangeRoleRequest();

        AdminUserResponse response =
                mock(AdminUserResponse.class);

        when(userService.changeRole(USER_ID, request))
                .thenReturn(response);

        AdminUserResponse result =
                adminUserController.changeRole(USER_ID, request);

        assertThat(result)
                .isSameAs(response);

        verify(userService)
                .changeRole(USER_ID, request);
    }

    @Test
    void shouldForceLogout() {

        adminUserController.forceLogout(USER_ID);

        verify(userService)
                .forceLogout(USER_ID);
    }
}