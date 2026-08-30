package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AdminUserResponse;
import com.ecommerce.auth.dto.ChangeRoleRequest;
import com.ecommerce.auth.dto.UpdateMeRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

  UserProfileResponse getMe(UUID userId);

  UserProfileResponse updateMe(UUID userId, UpdateMeRequest request);

  void deleteMe(UUID userId);

  Page<AdminUserResponse> getAllUsers(Pageable pageable);

  AdminUserResponse getUserById(UUID userId);

  void deleteUser(UUID userId);

  AdminUserResponse changeRole(UUID userId, ChangeRoleRequest request);

  void forceLogout(UUID userId);
}
