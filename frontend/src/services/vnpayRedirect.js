import { getErrorMessage } from './errorMessage';
import { createPaymentUrl } from './orderApi';

/**
 * Chuyển khách sang trang thanh toán VNPay của đơn. Không lấy được đường dẫn thì về trang chi tiết
 * đơn kèm lỗi, khách bấm "Thanh toán qua VNPay" để thử lại trong hạn thanh toán.
 */
export async function goToVnPay(orderId, navigate) {
  try {
    const { paymentUrl } = await createPaymentUrl(orderId);
    window.location.assign(paymentUrl);
  } catch (err) {
    navigate(`/orders/${orderId}`, {
      replace: true,
      state: { paymentError: getErrorMessage(err, 'Không mở được trang thanh toán VNPay') },
    });
  }
}
