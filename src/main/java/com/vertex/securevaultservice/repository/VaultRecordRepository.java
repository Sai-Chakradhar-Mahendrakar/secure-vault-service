package com.vertex.securevaultservice.repository;

import com.vertex.securevaultservice.entity.VaultRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;


@NoRepositoryBean
public interface VaultRecordRepository extends BaseRepository<VaultRecord, String> {
    Page<VaultRecord> findByUserId(String userId, Pageable pageable);
}
