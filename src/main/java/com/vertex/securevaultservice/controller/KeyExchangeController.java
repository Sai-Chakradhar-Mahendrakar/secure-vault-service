package com.vertex.securevaultservice.controller;

import com.vertex.securevaultservice.crypto.ServerEcdhKeyHolder;
import com.vertex.securevaultservice.response.ServerEcdhPublicKeyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/keys")
public class KeyExchangeController {
    private final ServerEcdhKeyHolder  serverEcdhKeyHolder;

    public KeyExchangeController(ServerEcdhKeyHolder serverEcdhKeyHolder) {
        this.serverEcdhKeyHolder = serverEcdhKeyHolder;
    }

    @GetMapping("/server/ecdh-public-key")
    public ResponseEntity<ServerEcdhPublicKeyResponse> getServerEcdhPublicKey() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ServerEcdhPublicKeyResponse(serverEcdhKeyHolder.getBase64PublicKey()));
    }
}
