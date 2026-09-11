package com.ecommerce.product.outbox;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface ProductOutboxRepository extends MongoRepository<ProductOutboxEvent, UUID> {}
