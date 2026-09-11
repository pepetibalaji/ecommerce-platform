package com.ecommerce.product.outbox;

import com.ecommerce.product.entity.Product;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductReconciliationService {
    private final MongoTemplate mongo;
    private final ProductOutboxService outbox;

    /** Bounded cursor scan repairs missed events without changing stock or product history. */
    @Transactional
    public Result reconcile(UUID afterId, int size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        Query query = new Query().with(Sort.by("id")).limit(size + 1);
        if (afterId != null) query.addCriteria(Criteria.where("id").gt(afterId));
        var products = mongo.find(query, Product.class);
        var batch = products.stream().limit(size).toList();
        batch.forEach(product -> outbox.enqueue(product, "product.reconciled"));
        return new Result(batch.size(), products.size() > size ? batch.getLast().getId() : null);
    }

    public record Result(int enqueued, UUID nextAfterId) {}
}
