package com.ecommerce.product.grpc;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.proto.product.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/** Private gRPC contract for Inventory's authoritative reconciliation only. */
@GrpcService @RequiredArgsConstructor
public class ProductSnapshotGrpcService extends ProductSnapshotServiceGrpc.ProductSnapshotServiceImplBase {
    private final ProductRepository products;
    @Override public void listInventorySnapshots(ProductSnapshotRequest request, StreamObserver<ProductSnapshotPage> observer) {
        int size=Math.max(1,Math.min(request.getSize()==0?100:request.getSize(),500));
        Page<Product> page=products.findAll(PageRequest.of(Math.max(0,request.getPage()),size));
        ProductSnapshotPage.Builder response=ProductSnapshotPage.newBuilder().setHasNext(page.hasNext()).setNextPage(page.getNumber()+1);
        for(Product p:page) response.addProducts(ProductSnapshot.newBuilder().setProductId(p.getId().toString())
                .setSellerId(p.getSellerId()==null?"":p.getSellerId().toString()).setActive(p.isActive())
                .setVersion((p.getVersion()==null?0:p.getVersion())+1).build());
        observer.onNext(response.build()); observer.onCompleted();
    }
}
