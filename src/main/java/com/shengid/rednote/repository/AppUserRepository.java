package com.shengid.rednote.repository;

import com.shengid.rednote.entity.AppUser;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from AppUser e where e.id=:id")
  Optional<AppUser> lock(@Param("id") long id);
}
