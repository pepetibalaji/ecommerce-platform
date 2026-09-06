package com.ecommerce.auth.repository;
import com.ecommerce.auth.entity.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoleRepository extends JpaRepository<Role, UUID> {
  Optional<Role> findByCode(String code);
  List<Role> findByCodeIn(Collection<String> codes);
}
