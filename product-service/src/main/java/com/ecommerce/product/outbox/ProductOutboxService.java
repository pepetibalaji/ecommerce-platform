package com.ecommerce.product.outbox;

import com.ecommerce.common.events.product.ProductLifecycleEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.product.entity.Product;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductOutboxService {
    public static final String TOPIC = KafkaTopics.PRODUCT_LIFECYCLE;
    private final ProductOutboxRepository repository;
    private final MongoTemplate mongo;
    private final KafkaTemplate<String, Object> kafka;
    private final MeterRegistry metrics;
    @Value("${product.outbox.batch-size:100}") private int batchSize = 100;
    @Value("${product.outbox.max-attempts:10}") private int maxAttempts = 10;
    @Value("${product.outbox.retry-delay-seconds:5}") private long retryDelaySeconds = 5;

    public ProductOutboxService(ProductOutboxRepository repository, MongoTemplate mongo,
            KafkaTemplate<String, Object> kafka, MeterRegistry metrics) {
        this.repository = repository;
        this.mongo = mongo;
        this.kafka = kafka;
        this.metrics = metrics;
        metrics.gauge("product.outbox.pending", this, source -> source.count(ProductOutboxEvent.Status.PENDING));
        metrics.gauge("product.outbox.dead", this, source -> source.count(ProductOutboxEvent.Status.DEAD));
        metrics.gauge("product.outbox.oldest.seconds", this, ProductOutboxService::oldestPendingAge);
    }

    // Product mutation and event snapshot must share the same database transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Product product, String type) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        repository.save(ProductOutboxEvent.builder().id(id).productId(product.getId())
                .sellerId(product.getSellerId()).type(type).occurredAt(now)
                .payload(snapshot(product, id, now, type, actorId())).build());
    }

    public ProductLifecycleEvent snapshot(Product product, UUID id, Instant now, String type, UUID actor) {
        return new ProductLifecycleEvent(id, product.getId(), product.getSellerId(), now,
                type, 1, (product.getVersion() == null ? 0 : product.getVersion()) + 1,
                product.isActive(), product.getName(), product.getPrice(), product.getCurrency(), actor,
                product.getDescription(), product.getCategory(), product.getBrand(),
                product.getImageUrls() == null ? List.of() : product.getImageUrls());
    }

    @Scheduled(fixedDelayString = "${product.outbox.poll-delay-ms:1000}", initialDelayString = "${product.outbox.initial-delay-ms:1000}")
    public void deliver() {
        for (int i = 0; i < Math.max(1, Math.min(batchSize, 1000)); i++) {
            ProductOutboxEvent event = claim();
            if (event == null) break;
            publish(event);
        }
    }

    ProductOutboxEvent claim() {
        Instant now = Instant.now();
        Criteria due = new Criteria().andOperator(Criteria.where("status").is(ProductOutboxEvent.Status.PENDING),
                new Criteria().orOperator(Criteria.where("nextAttemptAt").is(null), Criteria.where("nextAttemptAt").lte(now)));
        Criteria expired = new Criteria().andOperator(Criteria.where("status").is(ProductOutboxEvent.Status.PROCESSING),
                Criteria.where("leaseUntil").lt(now));
        Query query = Query.query(new Criteria().orOperator(due, expired)).with(Sort.by("occurredAt", "id"));
        return mongo.findAndModify(query, new Update().set("status", ProductOutboxEvent.Status.PROCESSING)
                .set("leaseToken", UUID.randomUUID()).set("leaseUntil", now.plusSeconds(60)).inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true), ProductOutboxEvent.class);
    }

    void publish(ProductOutboxEvent event) {
        Query owned = Query.query(Criteria.where("id").is(event.getId())
                .and("status").is(ProductOutboxEvent.Status.PROCESSING).and("leaseToken").is(event.getLeaseToken()));
        try {
            ProductLifecycleEvent payload = event.getPayload();
            if (payload == null) {
                // Pre-upgrade rows lack a snapshot: repair with current state and a reconciliation event.
                Product product = mongo.findById(event.getProductId(), Product.class);
                if (product == null) throw new IllegalStateException("Outbox product is unavailable");
                payload = snapshot(product, event.getId(), Instant.now(), "product.reconciled", null);
                if (mongo.updateFirst(owned, new Update().set("payload", payload), ProductOutboxEvent.class).getModifiedCount() == 0) return;
            }
            kafka.send(TOPIC, event.getProductId().toString(), payload).get(15, TimeUnit.SECONDS);
            long changed = mongo.updateFirst(owned, new Update().set("status", ProductOutboxEvent.Status.PUBLISHED)
                    .set("publishedAt", Instant.now()).unset("leaseUntil").unset("leaseToken").unset("lastError"),
                    ProductOutboxEvent.class).getModifiedCount();
            if (changed > 0) metrics.counter("product.outbox.delivered").increment();
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            boolean exhausted = event.getAttempts() >= Math.max(1, maxAttempts);
            long delay = Math.min(3600, Math.max(1, retryDelaySeconds) * (1L << Math.min(Math.max(0, event.getAttempts() - 1), 10)));
            mongo.updateFirst(owned, new Update().set("status", exhausted ? ProductOutboxEvent.Status.DEAD : ProductOutboxEvent.Status.PENDING)
                    .set("nextAttemptAt", Instant.now().plusSeconds(delay))
                    .set("lastError", failure.getClass().getSimpleName()).unset("leaseUntil").unset("leaseToken"), ProductOutboxEvent.class);
            metrics.counter("product.outbox.failures").increment();
        }
    }

    public long replayDeadLetters() {
        long count = mongo.updateMulti(Query.query(Criteria.where("status").is(ProductOutboxEvent.Status.DEAD)),
                new Update().set("status", ProductOutboxEvent.Status.PENDING).set("attempts", 0)
                        .unset("nextAttemptAt").unset("leaseUntil").unset("leaseToken"), ProductOutboxEvent.class).getModifiedCount();
        metrics.counter("product.outbox.replayed").increment(count);
        return count;
    }

    private UUID actorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("userId") != null)
            return UUID.fromString(jwt.getClaimAsString("userId"));
        return null;
    }

    private double count(ProductOutboxEvent.Status status) {
        return mongo.count(Query.query(Criteria.where("status").is(status)), ProductOutboxEvent.class);
    }

    private double oldestPendingAge() {
        ProductOutboxEvent first = mongo.findOne(Query.query(Criteria.where("status").in(
                ProductOutboxEvent.Status.PENDING, ProductOutboxEvent.Status.PROCESSING))
                .with(Sort.by("occurredAt")).limit(1), ProductOutboxEvent.class);
        return first == null || first.getOccurredAt() == null ? 0 : Math.max(0, Instant.now().getEpochSecond() - first.getOccurredAt().getEpochSecond());
    }
}
