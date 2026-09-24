package com.vertex.securevaultservice.repository.dao;

import com.vertex.securevaultservice.configuration.CustomThreadPool;
import com.vertex.securevaultservice.entity.VaultRecord;
import com.vertex.securevaultservice.repository.VaultRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Repository
public interface VaultRecordDao extends VaultRecordRepository {
    default CompletionStage<Page<VaultRecord>> getByUserId(String userId, Pageable pageable) {
        return CompletableFuture.supplyAsync(() -> findByUserId(userId, pageable), CustomThreadPool.getDatabaseExecutor())
                .thenApplyAsync(page -> page, CustomThreadPool.getComputationExecutor());
    }
}
