package com.shengid.rednote.repository;

import com.shengid.rednote.entity.FileAsset;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from FileAsset e where e.id=:id")
  Optional<FileAsset> lock(@Param("id") long id);

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select e from FileAsset e where e.id=:id")
  Optional<FileAsset> readLock(@Param("id") long id);
}
