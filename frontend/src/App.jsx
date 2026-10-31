import { Route, Routes } from 'react-router';
import AdminLayout from './components/AdminLayout';
import Layout from './components/Layout';
import RequireAdmin from './components/RequireAdmin';
import RequireCustomer from './components/RequireCustomer';
import RequireLogin from './components/RequireLogin';
import AddressesPage from './pages/AddressesPage';
import CartPage from './pages/CartPage';
import CheckoutPage from './pages/CheckoutPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import HealthCheckPage from './pages/HealthCheckPage';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import OffersPage from './pages/OffersPage';
import OrderDetailPage from './pages/OrderDetailPage';
import OrdersPage from './pages/OrdersPage';
import ProductDetailPage from './pages/ProductDetailPage';
import ProductsPage from './pages/ProductsPage';
import ProfilePage from './pages/ProfilePage';
import RegisterPage from './pages/RegisterPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import SurveyPage from './pages/SurveyPage';
import VnPayReturnPage from './pages/VnPayReturnPage';
import WishlistPage from './pages/WishlistPage';
import BrandAdminPage from './pages/admin/BrandAdminPage';
import CategoryAdminPage from './pages/admin/CategoryAdminPage';
import CustomerDetailPage from './pages/admin/CustomerDetailPage';
import CustomerListPage from './pages/admin/CustomerListPage';
import DashboardPage from './pages/admin/DashboardPage';
import OrderAdminDetailPage from './pages/admin/OrderAdminDetailPage';
import OrderListPage from './pages/admin/OrderListPage';
import ProductCreatePage from './pages/admin/ProductCreatePage';
import ProductEditPage from './pages/admin/ProductEditPage';
import ProductListPage from './pages/admin/ProductListPage';
import RecommendationStatsPage from './pages/admin/RecommendationStatsPage';
import SegmentDetailPage from './pages/admin/SegmentDetailPage';
import SegmentPage from './pages/admin/SegmentPage';
import VoucherAdminPage from './pages/admin/VoucherAdminPage';

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="products/:id" element={<ProductDetailPage />} />
        <Route path="cart" element={<CartPage />} />
        <Route
          path="checkout"
          element={
            <RequireCustomer>
              <CheckoutPage />
            </RequireCustomer>
          }
        />
        <Route
          path="orders"
          element={
            <RequireCustomer>
              <OrdersPage />
            </RequireCustomer>
          }
        />
        <Route
          path="orders/:id"
          element={
            <RequireCustomer>
              <OrderDetailPage />
            </RequireCustomer>
          }
        />
        <Route
          path="addresses"
          element={
            <RequireCustomer>
              <AddressesPage />
            </RequireCustomer>
          }
        />
        <Route
          path="wishlist"
          element={
            <RequireCustomer>
              <WishlistPage />
            </RequireCustomer>
          }
        />
        <Route
          path="offers"
          element={
            <RequireCustomer>
              <OffersPage />
            </RequireCustomer>
          }
        />
        <Route path="survey" element={<SurveyPage />} />
        <Route path="payment/vnpay-return" element={<VnPayReturnPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="forgot-password" element={<ForgotPasswordPage />} />
        <Route path="reset-password" element={<ResetPasswordPage />} />
        <Route
          path="profile"
          element={
            <RequireLogin>
              <ProfilePage />
            </RequireLogin>
          }
        />
        <Route path="health" element={<HealthCheckPage />} />
        <Route
          path="admin"
          element={
            <RequireAdmin>
              <AdminLayout />
            </RequireAdmin>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="products" element={<ProductListPage />} />
          <Route path="products/new" element={<ProductCreatePage />} />
          <Route path="products/:id" element={<ProductEditPage />} />
          <Route path="categories" element={<CategoryAdminPage />} />
          <Route path="brands" element={<BrandAdminPage />} />
          <Route path="orders" element={<OrderListPage />} />
          <Route path="orders/:id" element={<OrderAdminDetailPage />} />
          <Route path="customers" element={<CustomerListPage />} />
          <Route path="customers/:id" element={<CustomerDetailPage />} />
          <Route path="segments" element={<SegmentPage />} />
          <Route path="segments/:code" element={<SegmentDetailPage />} />
          <Route path="vouchers" element={<VoucherAdminPage />} />
          <Route path="recommendations" element={<RecommendationStatsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export default App;
