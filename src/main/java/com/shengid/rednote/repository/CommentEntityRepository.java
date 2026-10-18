package com.shengid.rednote.repository;

import com.shengid.rednote.entity.CommentEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CommentEntityRepository extends JpaRepository<CommentEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CommentEntity e where e.id=:id")
  Optional<CommentEntity> lock(@Param("id") long id);
}
