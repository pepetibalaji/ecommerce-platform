# Pepekart sample products

Two raw JSON arrays for the seller bulk importer:

- `products-001-050.json`: first 50 products.
- `products-051-100.json`: second 50 products.

Open one file, copy its entire JSON array into **Products JSON**, review the preview, and import it. Repeat with the other file.

There are 100 unique product names across these files, with 25 products in each exact storefront category: Apparel, Home, Accessories, and Stationery. Both batches contain all four categories. These are sample data with illustrative INR prices.

The product service must allow `cdn.dummyjson.com` and `images.pexels.com`. The local VS Code product-service launch profile includes both hosts; restart product-service from that profile before importing. For a separately launched service, include those hosts in its `product.images.allowed-hosts` setting while preserving other approved hosts.

Images are third-party sample assets. Stationery products and the sunglasses duo use illustrative photos; some variants intentionally share an image. Network availability can change.

Sources:
- [DummyJSON sample product data](https://dummyjson.com/docs/products)
- [Notebook photo](https://www.pexels.com/photo/colorful-pens-and-notebook-5594263/)
- [Colour pencils and paper clips](https://www.pexels.com/photo/photo-of-color-pencils-and-colorful-paper-clips-7054779/)
- [Clipboard stationery](https://www.pexels.com/photo/pins-and-paper-clips-on-a-clipboard-8099492/)
- [Planner and binder clips](https://www.pexels.com/photo/pieces-of-paper-and-binder-clips-8461841/)
- [Pens and notebook](https://www.pexels.com/photo/colored-pens-on-a-notebook-5554753/)
- [Pencil pouch](https://www.pexels.com/photo/a-pencil-case-on-an-open-notebook-5963052/)
- [Fountain pen](https://www.pexels.com/photo/pen-on-a-sheet-of-paper-17750884/)
- [Leather journal](https://www.pexels.com/photo/close-up-of-leather-notebook-on-wooden-desk-33812671/)
