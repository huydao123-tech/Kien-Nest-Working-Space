package com.cowork.dataaccess.repository;

import com.cowork.dataaccess.entity.Resource;
import com.cowork.dataaccess.entity.ResourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository {

    Optional<Resource> findById(UUID id);

    /** Đọc và khóa ghi hàng của vị trí đến hết giao dịch (SELECT ... FOR UPDATE). */
    Optional<Resource> findByIdForUpdate(UUID id);

    List<Resource> findBookable(ResourceType type, int minCapacity);

    List<Resource> findAll();

    Resource save(Resource resource);

    long count();
}
