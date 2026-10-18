package com.shengid.rednote.repository;

import com.shengid.rednote.entity.NoteImage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NoteImageRepository extends JpaRepository<NoteImage, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from NoteImage e where e.id=:id")
  Optional<NoteImage> lock(@Param("id") long id);
}
