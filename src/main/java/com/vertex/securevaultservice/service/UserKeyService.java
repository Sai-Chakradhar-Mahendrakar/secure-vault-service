package com.vertex.securevaultservice.service;

import com.vertex.securevaultservice.request.AddUserPublicKeyRequest;
import com.vertex.securevaultservice.response.UserKeyResponse;

import java.util.concurrent.CompletionStage;

public interface UserKeyService {
    CompletionStage<UserKeyResponse> addUserKey(String userId, AddUserPublicKeyRequest addUserPublicKeyRequest);
}
