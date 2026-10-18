package com.shengid.rednote.repository;

import com.shengid.rednote.entity.NoteEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NoteEntityRepository extends JpaRepository<NoteEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from NoteEntity e where e.id=:id")
  Optional<NoteEntity> lock(@Param("id") long id);
}
