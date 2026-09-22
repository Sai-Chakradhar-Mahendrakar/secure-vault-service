package com.vertex.securevaultservice.repository;

import com.vertex.securevaultservice.entity.VaultRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface VaultRecordRepository extends JpaRepository<VaultRecord, String> {

}
