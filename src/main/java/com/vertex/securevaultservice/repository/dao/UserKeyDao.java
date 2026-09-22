package com.vertex.securevaultservice.repository.dao;

import com.vertex.securevaultservice.configuration.CustomThreadPool;
import com.vertex.securevaultservice.entity.UserKey;
import com.vertex.securevaultservice.repository.UserKeyRepository;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Repository
public interface UserKeyDao extends UserKeyRepository {
    default CompletionStage<UserKey> getByUserId(String userId) {
        return CompletableFuture.supplyAsync(() -> findByUserId(userId), CustomThreadPool.getDatabaseExecutor())
                .thenApplyAsync(userKey -> userKey, CustomThreadPool.getComputationExecutor());
    }
}
