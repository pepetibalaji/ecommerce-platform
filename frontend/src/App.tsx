import { BrowserRouter, Link, Navigate, Route, Routes } from "react-router-dom";
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
    <main className="route-message page-container">
      <span className="eyebrow">Access restricted</span>
      <h1>You do not have access to that workspace.</h1>
      <p>Use an account with the required role, or return to the customer storefront.</p>
      <Link className="button button-primary" to="/">Go to storefront</Link>
    </main>
  );
}

function MissingPage() {
  return (
    <main className="route-message page-container">
      <span className="eyebrow">404</span>
      <h1>That page is not available.</h1>
      <p>The link may be old, or the resource may no longer be available in this stage environment.</p>
      <Link className="button button-primary" to="/">Browse the catalogue</Link>
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
