package com.vertex.securevaultservice.repository;

import com.vertex.securevaultservice.entity.UserKey;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface UserKeyRepository extends BaseRepository<UserKey, String> {
    UserKey findByUserId(String userId);
}
