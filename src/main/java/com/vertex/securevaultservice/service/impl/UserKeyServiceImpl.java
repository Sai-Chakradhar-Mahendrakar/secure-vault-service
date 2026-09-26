package com.vertex.securevaultservice.service.impl;

import com.vertex.securevaultservice.entity.UserKey;
import com.vertex.securevaultservice.repository.dao.UserKeyDao;
import com.vertex.securevaultservice.request.AddUserPublicKeyRequest;
import com.vertex.securevaultservice.response.UserKeyResponse;
import com.vertex.securevaultservice.service.UserKeyService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

@Service
public class UserKeyServiceImpl implements UserKeyService {
    private final UserKeyDao userKeyDao;

    public UserKeyServiceImpl(UserKeyDao userKeyDao) {
        this.userKeyDao = userKeyDao;
    }

    @Override
    public CompletionStage<Optional<UserKey>> findById(String userId) {
        return userKeyDao.getByUserId(userId)
                .thenApply(Optional::ofNullable);
    }

    @Override
    public CompletionStage<UserKeyResponse> upsertUserKey(String userId, AddUserPublicKeyRequest addUserPublicKeyRequest) {
        return userKeyDao.getByUserId(userId)
                .thenCompose(existingUserKey -> {
                    UserKey userKeyToPersist = existingUserKey != null
                            ? existingUserKey.toBuilder()
                                    .rsaPublicKey(coalesce(addUserPublicKeyRequest.rsaPublicKey(), existingUserKey.getRsaPublicKey()))
                                    .ecdsaPublicKey(coalesce(addUserPublicKeyRequest.ecdsaPublicKey(), existingUserKey.getEcdsaPublicKey()))
                                    .ecdhPublicKey(coalesce(addUserPublicKeyRequest.ecdhPublicKey(), existingUserKey.getEcdhPublicKey()))
                                    .build()
                            : addUserPublicKeyRequest.toEntity(userId);
                    return userKeyDao.persist(userKeyToPersist);
                })
                .thenApply(UserKeyResponse::from);
    }

    private static String coalesce(String newValue, String existingValue) {
        return newValue != null ? newValue : existingValue;
    }
}
