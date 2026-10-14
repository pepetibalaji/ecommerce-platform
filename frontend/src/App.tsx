import { BrowserRouter, Link, Navigate, Route, Routes } from "react-router-dom";
import { ArrowLeft, ArrowUpRight, Compass, LockKeyhole, ShoppingBag, Sparkles } from "lucide-react";
import { AuthProvider } from "./auth/AuthProvider";
import { CartProvider } from "./cart/CartProvider";
import { RequireAuth, RequireRole } from "./components/guards";
import { BackofficeLayout, StorefrontLayout } from "./components/layouts";
import {
  AdminCataloguePage,
  AdminInventoryPage,
  AdminNotificationsPage,
  AdminOrdersPage,
  AdminOverviewPage,
  AdminPaymentsPage,
  AdminUserDetailPage,
  AdminUsersPage,
  SellerInventoryPage,
  SellerOrdersPage,
  SellerOverviewPage,
  SellerBulkImportPage,
  SellerProductEditorPage,
  SellerProductsPage,
} from "./pages/backoffice";
import {
  AccountPage,
  CheckoutPage,
  OrderDetailPage,
  OrdersPage,
  PaymentReturnPage,
} from "./pages/customer-account";
import {
  CartPage,
  CataloguePage,
  ForgotPasswordPage,
  LinkActionPage,
  LoginPage,
  ProductDetailPage,
  RegisterPage,
} from "./pages/storefront";

function AccessDeniedPage() {
  return (
    <section className="route-message page-container route-experience">
      <div className="route-art route-art-restricted" aria-hidden="true"><span>Private<br /><em>workspace.</em></span><LockKeyhole size={86} strokeWidth={1} /><Sparkles size={26} /></div>
      <div className="route-copy"><span className="eyebrow">Your next step / Access restricted</span><h1>This space needs<br />a different key.</h1><p>Your account does not have access to this workspace. You can sign in with an eligible account or keep exploring the storefront.</p><Link className="button button-primary" to="/login">Go to sign in <ArrowUpRight size={17} aria-hidden="true" /></Link><Link className="route-return" to="/"><ArrowLeft size={15} aria-hidden="true" />Return to Pepekart</Link></div>
    </section>
  );
}

function MissingPage() {
  return (
    <main className="route-message page-container route-experience">
      <div className="route-art" aria-hidden="true"><span>404</span><Compass size={86} strokeWidth={1} /><Sparkles size={26} /></div>
      <div className="route-copy"><Link className="route-brand" to="/"><ShoppingBag size={20} aria-hidden="true" />pepekart.</Link><span className="eyebrow">A little detour / Page not found</span><h1>A good find.<br />A missing page.</h1><p>This page has moved, or the link is no longer available. There’s still a world of good things waiting for you.</p><Link className="button button-primary" to="/">Explore the collection <ArrowUpRight size={17} aria-hidden="true" /></Link><Link className="route-return" to="/cart"><ArrowLeft size={15} aria-hidden="true" />Back to your bag</Link></div>
    </main>
  );
}

/**
 * Application routing deliberately keeps the public storefront, seller workspace,
 * and administration workspace as separate layout trees. Authorization is enforced
 * before a back-office layout is rendered; links are merely a convenience, not a
 * security boundary.
 */
export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <CartProvider>
          <Routes>
            <Route element={<StorefrontLayout />}>
              <Route index element={<CataloguePage />} />
              <Route path="products/:productId" element={<ProductDetailPage />} />
              <Route path="cart" element={<CartPage />} />

              <Route path="login" element={<LoginPage />} />
              <Route path="register" element={<RegisterPage />} />
              <Route path="forgot-password" element={<ForgotPasswordPage />} />
              <Route path="verify-email" element={<LinkActionPage action="verify" />} />
              <Route path="reset-password" element={<LinkActionPage action="reset" />} />
              <Route path="confirm-email-change" element={<LinkActionPage action="email-change" />} />
              <Route path="access-denied" element={<AccessDeniedPage />} />

              <Route element={<RequireAuth />}>
                <Route path="checkout" element={<CheckoutPage />} />
                <Route path="payment/return" element={<PaymentReturnPage />} />
                <Route path="orders" element={<OrdersPage />} />
                <Route path="orders/:orderId" element={<OrderDetailPage />} />
                <Route path="account" element={<AccountPage />} />
              </Route>
            </Route>

            <Route element={<RequireAuth />}>
              <Route element={<RequireRole roles={["SELLER", "ADMIN"]} />}>
                <Route element={<BackofficeLayout area="seller" />}>
                  <Route path="seller" element={<SellerOverviewPage />} />
                  <Route path="seller/products" element={<SellerProductsPage />} />
                  <Route element={<RequireRole roles={["SELLER"]} />}>
                    <Route path="seller/products/import" element={<SellerBulkImportPage />} />
                  </Route>
                  <Route path="seller/products/new" element={<SellerProductEditorPage mode="create" />} />
                  <Route path="seller/products/:productId/edit" element={<SellerProductEditorPage mode="edit" />} />
                  <Route path="seller/products/:productId/inventory" element={<SellerInventoryPage />} />
                  <Route path="seller/inventory" element={<SellerInventoryPage />} />
                  <Route path="seller/orders" element={<SellerOrdersPage />} />
                </Route>
              </Route>

              <Route element={<RequireRole roles={["ADMIN"]} />}>
                <Route element={<BackofficeLayout area="admin" />}>
                  <Route path="admin" element={<AdminOverviewPage />} />
                  <Route path="admin/users" element={<AdminUsersPage />} />
                  <Route path="admin/users/:userId" element={<AdminUserDetailPage />} />
                  <Route path="admin/catalogue" element={<AdminCataloguePage />} />
                  <Route path="admin/catalogue/new" element={<AdminCataloguePage mode="create" />} />
                  <Route path="admin/catalogue/:productId/edit" element={<AdminCataloguePage mode="edit" />} />
                  <Route path="admin/inventory" element={<AdminInventoryPage />} />
                  <Route path="admin/orders" element={<AdminOrdersPage />} />
                  <Route path="admin/payments" element={<AdminPaymentsPage />} />
                  <Route path="admin/notifications" element={<AdminNotificationsPage />} />
                </Route>
              </Route>
            </Route>

            <Route path="/home" element={<Navigate to="/" replace />} />
            <Route path="*" element={<MissingPage />} />
          </Routes>
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
