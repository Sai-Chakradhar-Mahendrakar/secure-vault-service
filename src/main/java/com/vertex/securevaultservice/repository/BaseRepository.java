package com.vertex.securevaultservice.repository;

import com.vertex.securevaultservice.configuration.CustomThreadPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@NoRepositoryBean
public interface BaseRepository<T, I> extends JpaRepository<T, I>, JpaSpecificationExecutor<T> {
    default CompletionStage<T> persist(T t) {
        return CompletableFuture.supplyAsync(() -> save(t), CustomThreadPool.getDatabaseExecutor())
                .thenApplyAsync(result -> result, CustomThreadPool.getComputationExecutor());
    }

    default CompletionStage<Optional<T>> findWithId(I id) {
        return CompletableFuture.supplyAsync(() -> findById(id), CustomThreadPool.getDatabaseExecutor())
                .thenApplyAsync(result -> result, CustomThreadPool.getComputationExecutor());
    }

    default CompletionStage<T> remove(T t) {
        return CompletableFuture.supplyAsync(() -> {
            delete(t);
            return t;
        }, CustomThreadPool.getDatabaseExecutor()).thenApplyAsync(result -> result, CustomThreadPool.getComputationExecutor());
    }
}
