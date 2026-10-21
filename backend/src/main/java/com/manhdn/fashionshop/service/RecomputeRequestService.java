package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.entity.RecomputeTask;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ghi yêu cầu tính lại gợi ý hoặc gán nhóm vào bảng recompute_request (BR-20, BR-23; Mục 3.15),
 * trong cùng giao dịch với hành động của người dùng. Dịch vụ học máy lấy các yêu cầu đã đến hạn để
 * tính; backend không gọi dịch vụ học máy và không chờ tính.
 *
 * <p>Đây là hàm "ghi yêu cầu" duy nhất của backend; mọi chỗ gọi (lượt xem, giỏ hàng, đơn hàng, yêu
 * thích, khảo sát) đều đi qua {@link #request}. Yêu cầu không được ghi ngay: chúng được gom theo
 * giao dịch đang chạy (cùng tài khoản và loại việc thì giữ thời gian chờ ngắn nhất), rồi ghi hết
 * ngay trước khi giao dịch ngoài cùng commit, theo thứ tự (tài khoản, loại việc) tăng dần. Nhờ vậy
 * các hàm nghiệp vụ lồng nhau không khóa bảng theo thứ tự lộn xộn (tránh khóa chéo), và giao dịch
 * bị hủy thì không yêu cầu nào được ghi.
 *
 * <p>Lệnh ghi chạy bằng {@link NamedParameterJdbcTemplate} trên chính kết nối của giao dịch, không
 * qua EntityManager: truy vấn native của Hibernate ở bước này buộc Hibernate flush sớm và để lọt
 * exception chưa được Spring dịch (vd vi phạm UNIQUE không còn là DataIntegrityViolationException).
 * Thời điểm đến hạn tính bằng {@code UTC_TIMESTAMP(3)}, không dùng {@code NOW()} (đổi theo múi giờ
 * của từng kết nối).
 */
@Service
public class RecomputeRequestService {

  /** Thời gian chờ của hành động rõ ý: đặt đơn, đổi giỏ hàng, yêu thích, khảo sát (BR-20). */
  public static final Duration STRONG_ACTION_DELAY = Duration.ofSeconds(3);

  /** Thời gian chờ tối đa với lượt xem (BR-20). */
  public static final Duration VIEW_DELAY = Duration.ofMinutes(5);

  /**
   * Gộp vào yêu cầu đang chờ (nếu có) mà không làm lùi thời điểm đến hạn, và tăng version để dịch
   * vụ học máy biết có yêu cầu mới đến trong lúc tính.
   */
  private static final String UPSERT_SQL =
      """
      INSERT INTO recompute_request (account_id, task_type, due_at, version, attempts, created_at)
      VALUES (:accountId, :taskType, TIMESTAMPADD(MICROSECOND, :delayMicros, UTC_TIMESTAMP(3)),
              1, 0, UTC_TIMESTAMP(3))
      AS incoming
      ON DUPLICATE KEY UPDATE due_at = LEAST(recompute_request.due_at, incoming.due_at),
                              version = recompute_request.version + 1
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  /** Nhận NamedParameterJdbcTemplate qua constructor. */
  public RecomputeRequestService(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Yêu cầu tính lại một loại việc cho tài khoản, đến hạn sau thời gian chờ cho trước tính từ lúc
   * giao dịch commit. Gọi nhiều lần trong một giao dịch cho cùng tài khoản và loại việc chỉ ghi 1
   * dòng, với thời gian chờ ngắn nhất. Giao dịch bị hủy thì không ghi gì.
   *
   * @param accountId mã tài khoản
   * @param task loại việc
   * @param delay thời gian chờ, thường là {@link #STRONG_ACTION_DELAY} hoặc {@link #VIEW_DELAY}
   * @throws IllegalStateException nếu không có giao dịch đọc-ghi đang chạy
   */
  public void request(long accountId, RecomputeTask task, Duration delay) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()
        || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      throw new IllegalStateException(
          "Yêu cầu tính lại phải được ghi trong một giao dịch đọc-ghi đang chạy");
    }
    pendingRequests().add(new RequestKey(accountId, task), delay);
  }

  /** Tập yêu cầu của giao dịch hiện tại; tạo và đăng ký với giao dịch ở lần gọi đầu tiên. */
  private PendingRequests pendingRequests() {
    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      if (synchronization instanceof PendingRequests pending && pending.belongsTo(this)) {
        return pending;
      }
    }
    PendingRequests pending = new PendingRequests();
    TransactionSynchronizationManager.registerSynchronization(pending);
    return pending;
  }

  /** Khóa của một yêu cầu, sắp theo tài khoản rồi loại việc (thứ tự khóa chính của bảng). */
  private record RequestKey(long accountId, RecomputeTask task) {

    static final Comparator<RequestKey> ORDER =
        Comparator.comparingLong(RequestKey::accountId).thenComparing(RequestKey::task);
  }

  /** Các yêu cầu đang gom của một giao dịch, ghi xuống CSDL ngay trước khi giao dịch commit. */
  private final class PendingRequests implements TransactionSynchronization {

    private final Map<RequestKey, Duration> delays = new TreeMap<>(RequestKey.ORDER);

    void add(RequestKey key, Duration delay) {
      delays.merge(key, delay, (current, added) -> added.compareTo(current) < 0 ? added : current);
    }

    boolean belongsTo(RecomputeRequestService service) {
      return RecomputeRequestService.this == service;
    }

    @Override
    public void beforeCommit(boolean readOnly) {
      delays.forEach(
          (key, delay) ->
              jdbcTemplate.update(
                  UPSERT_SQL,
                  new MapSqlParameterSource()
                      .addValue("accountId", key.accountId())
                      .addValue("taskType", key.task().name())
                      .addValue("delayMicros", delay.toNanos() / 1_000)));
    }
  }
}
