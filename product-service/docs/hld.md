# High-Level Design

Product Service is the catalog and current-price authority. It owns product identity, descriptive
data, seller ownership, availability, and the price Order Service uses during checkout. Public
catalog reads are separate from administrative and seller-management routes.

Administrators can manage all products and explicitly choose the seller owner. Seller routes use
the JWT `userId` as the owner and cannot expose or mutate another seller's products. On successful
creation, Product Service publishes `product-created` so Inventory Service provisions zero stock.
