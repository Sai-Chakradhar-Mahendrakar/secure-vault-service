package com.vertex.securevaultservice.controller;

import com.vertex.securevaultservice.entity.UserKey;
import com.vertex.securevaultservice.request.AddUserPublicKeyRequest;
import com.vertex.securevaultservice.response.UserKeyResponse;
import com.vertex.securevaultservice.service.UserKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletionStage;

@RestController
@RequestMapping("/api/v1/users")
public class UserKeyController {
    private final UserKeyService userKeyService;

    public UserKeyController(UserKeyService userKeyService) {
        this.userKeyService = userKeyService;
    }

    @PostMapping("/{userId}/keys")
    public CompletionStage<ResponseEntity<UserKeyResponse>> upsertUserKey(
            @PathVariable String userId,
            @RequestBody AddUserPublicKeyRequest addUserPublicKeyRequest) {
        return userKeyService.upsertUserKey(userId, addUserPublicKeyRequest)
                .thenApply(userKey -> ResponseEntity.status(HttpStatus.CREATED).body(userKey));
    }
}
