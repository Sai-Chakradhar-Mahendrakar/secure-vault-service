package com.vertex.securevaultservice.service;

import com.vertex.securevaultservice.entity.UserKey;
import com.vertex.securevaultservice.request.AddUserPublicKeyRequest;
import com.vertex.securevaultservice.response.UserKeyResponse;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface UserKeyService {
    CompletionStage<Optional<UserKey>> findById(String userId);

    CompletionStage<UserKeyResponse> upsertUserKey(String userId, AddUserPublicKeyRequest addUserPublicKeyRequest);
}
