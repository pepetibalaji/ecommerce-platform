package com.ecommerce.inventory.service;

import com.ecommerce.common.grpc.factory.GrpcClientFactory;
import com.ecommerce.proto.product.*;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class ProductSnapshotGrpcClient {
    record Snapshot(UUID productId, UUID sellerId, boolean active, long version) { }
    private final GrpcClientFactory grpc;
    ProductSnapshotGrpcClient(GrpcClientFactory grpc) { this.grpc=grpc; }
    List<Snapshot> fetchAll() {
        List<Snapshot> all=new ArrayList<>(); int page=0;
        while(true) {
            Metadata headers=new Metadata(); headers.put(Metadata.Key.of("x-internal-caller", Metadata.ASCII_STRING_MARSHALLER), "inventory-service");
            ProductSnapshotPage response=grpc.stub("product-snapshot", ProductSnapshotServiceGrpc::newBlockingStub)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                    .listInventorySnapshots(ProductSnapshotRequest.newBuilder().setPage(page).setSize(500).build());
            for(ProductSnapshot p:response.getProductsList()) all.add(new Snapshot(UUID.fromString(p.getProductId()),
                    p.getSellerId().isBlank()?null:UUID.fromString(p.getSellerId()),p.getActive(),p.getVersion()));
            if(!response.getHasNext()) return all; page=response.getNextPage();
        }
    }
}
