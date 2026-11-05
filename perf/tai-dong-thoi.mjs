// Kiểm thử tải: N khách dùng web cùng lúc, đo thời gian phản hồi P95 theo nhóm thao tác và so
// với chỉ tiêu ở mục 2.2.3 của báo cáo (Bảng 2.9). Chỉ dùng Node 22+ (fetch có sẵn), không cần cài gì.
//
//   node perf/tai-dong-thoi.mjs --base http://localhost:8080 --users 50 --duration 180
//   node perf/tai-dong-thoi.mjs --base https://<tên miền staging> --read-only   (không tạo dữ liệu)
//
// Mỗi khách ảo: đăng ký tài khoản @tai.test, thêm địa chỉ, rồi lặp lại như người thật: xem danh
// sách, tìm kiếm, mở sản phẩm (ghi lượt xem, xem gợi ý liên quan), xem trang chủ (gợi ý cá nhân);
// cứ 4 vòng thì thêm vào giỏ, xem "thường được mua cùng", xem trước và đặt 1 đơn COD.
// Giữa 2 thao tác nghỉ ngẫu nhiên 0,5–1,5 lần --think-ms (mặc định 1 giây) như người đọc trang.
// Chế độ thường ĐẶT ĐƠN THẬT (trừ tồn kho): chỉ chạy trên CSDL thử, không chạy trên staging thật.
// --read-only: không đăng nhập, chỉ các API công khai đọc dữ liệu và gợi ý liên quan.
// Kết quả: bảng in ra màn hình; --out <file.json> lưu thêm số liệu thô theo nhóm.

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => {
    if (a.startsWith("--"))
      acc.push([
        a.slice(2),
        all[i + 1]?.startsWith("--") || all[i + 1] === undefined
          ? "true"
          : all[i + 1],
      ]);
    return acc;
  }, []),
);
const BASE = (args.base || "http://localhost:8080").replace(/\/$/, "");
const USERS = Number(args.users || 50);
const DURATION_S = Number(args.duration || 180);
const RAMP_S = Number(args.ramp || 30);
const THINK_MS = Number(args["think-ms"] || 1000);
const READ_ONLY = args["read-only"] === "true";
const RUN = Date.now().toString(36);

// Chỉ tiêu P95 (ms) theo Bảng 2.9: đọc < 1 s, gợi ý tính khi yêu cầu < 1,5 s, có giao dịch < 2 s.
const GROUPS = {
  doc: {
    label: "Đọc dữ liệu (danh sách, tìm kiếm, chi tiết, gợi ý trang chủ)",
    limit: 1000,
  },
  goiY: { label: "Gợi ý tính khi yêu cầu (liên quan, mua cùng)", limit: 1500 },
  ghi: {
    label: "Ghi có giao dịch (đăng ký, địa chỉ, lượt xem, giỏ hàng)",
    limit: 2000,
  },
  datHang: { label: "Xác nhận đơn hàng (POST /api/orders)", limit: 2000 },
};
const samples = Object.fromEntries(Object.keys(GROUPS).map((k) => [k, []]));
const errors = Object.fromEntries(Object.keys(GROUPS).map((k) => [k, 0]));
// 409 khi đặt hàng là từ chối đúng quy tắc (hết hàng, giỏ bị loại dòng hết hàng, giá đổi: BR-08,
// BR-11), không phải lỗi hệ thống: đếm riêng, không làm trượt chỉ tiêu.
const rejected = Object.fromEntries(Object.keys(GROUPS).map((k) => [k, 0]));
const errorDetails = new Map();
let measuring = false;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const think = () => sleep(THINK_MS * (0.5 + Math.random()));
const pick = (list) => list[Math.floor(Math.random() * list.length)];

async function call(
  group,
  method,
  path,
  { token, body, allow409 = false } = {},
) {
  const started = performance.now();
  let res;
  try {
    res = await fetch(BASE + path, {
      method,
      headers: {
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    const ms = performance.now() - started;
    if (measuring && group) samples[group].push(ms);
    if (res.status === 409 && allow409) {
      if (measuring && group) rejected[group] += 1;
      return null;
    }
    if (!res.ok) throw new Error(`${res.status} ${text.slice(0, 120)}`);
    return text ? JSON.parse(text) : null;
  } catch (e) {
    if (measuring && group) {
      if (!res) samples[group].push(performance.now() - started);
      errors[group] += 1;
      const key = `${method} ${path.replace(/\d+/g, "{id}")}: ${e.message.slice(0, 80)}`;
      errorDetails.set(key, (errorDetails.get(key) || 0) + 1);
    }
    return null;
  }
}

async function loadCatalog() {
  const ids = [];
  for (let page = 0; page < 5; page++) {
    const data = await call(null, "GET", `/api/products?page=${page}&size=40`);
    if (!data)
      throw new Error(
        `Không đọc được ${BASE}/api/products: backend chưa chạy?`,
      );
    ids.push(...data.content.map((p) => p.id));
  }
  return ids;
}

async function virtualUser(index, productIds, stopAt) {
  let token = null;
  let addressId = null;
  if (!READ_ONLY) {
    const email = `tai-${RUN}-${index}@tai.test`;
    const auth = await call("ghi", "POST", "/api/auth/register", {
      body: {
        email,
        password: `TaiThu${RUN}1`,
        fullName: `Khách tải ${index}`,
      },
    });
    token = auth?.accessToken;
    const address = token
      ? await call("ghi", "POST", "/api/addresses", {
          token,
          body: {
            recipientName: `Khách tải ${index}`,
            phone: "0901234567",
            addressDetail: "1 Võ Văn Ngân, Thủ Đức",
            isDefault: true,
          },
        })
      : null;
    addressId = address?.id;
  }
  const keywords = ["giay", "tui", "balo", "dep", "vi", "non", "that lung"];
  for (let round = 1; Date.now() < stopAt; round++) {
    await call(
      "doc",
      "GET",
      `/api/products?page=${Math.floor(Math.random() * 20)}&size=20`,
    );
    await think();
    await call(
      "doc",
      "GET",
      `/api/products?keyword=${encodeURIComponent(pick(keywords))}&size=20`,
    );
    await think();
    const id = pick(productIds);
    const product = await call("doc", "GET", `/api/products/${id}`);
    if (token)
      await call("ghi", "POST", `/api/products/${id}/views`, { token });
    await call("goiY", "GET", `/api/products/${id}/related`);
    await think();
    if (token) {
      await call("doc", "GET", "/api/recommendations/me", { token });
      await think();
    }
    if (token && addressId && round % 4 === 0 && product) {
      const variant = product.variants?.find((v) => v.stock > 0);
      if (variant) {
        await call("ghi", "POST", "/api/cart/items", {
          token,
          body: { variantId: variant.id, quantity: 1 },
        });
        await call(
          "goiY",
          "GET",
          `/api/products/bought-together?productIds=${id}`,
        );
        await think();
        const preview = await call("doc", "GET", "/api/orders/preview", {
          token,
        });
        if (preview) {
          await call("datHang", "POST", "/api/orders", {
            token,
            allow409: true,
            body: {
              idempotencyKey: crypto.randomUUID(),
              paymentMethod: "COD",
              addressId,
              expectedTotal: preview.total,
            },
          });
        }
        await think();
      }
    }
  }
}

function percentile(sorted, p) {
  if (!sorted.length) return NaN;
  return sorted[
    Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  ];
}

async function main() {
  const productIds = await loadCatalog();
  console.log(
    `${BASE}: ${USERS} khách đồng thời, tăng dần trong ${RAMP_S} s, đo ${DURATION_S} s${READ_ONLY ? " (chỉ đọc)" : ""}`,
  );
  const rampEnd = Date.now() + RAMP_S * 1000;
  const stopAt = rampEnd + DURATION_S * 1000;
  const users = [];
  for (let i = 0; i < USERS; i++) {
    users.push(
      sleep((RAMP_S * 1000 * i) / USERS).then(() =>
        virtualUser(i, productIds, stopAt),
      ),
    );
  }
  // Chỉ đo sau khi đủ N khách (bỏ giai đoạn khởi động, JVM còn đang biên dịch nóng).
  await sleep(RAMP_S * 1000);
  measuring = true;
  const started = Date.now();
  await Promise.all(users);
  measuring = false;
  const seconds = (Date.now() - started) / 1000;

  const result = {};
  let ok = true;
  console.log(
    "\nNhóm thao tác | Số yêu cầu | Lỗi | Từ chối 409 | TB (ms) | P50 | P95 | P99 | Max | Chỉ tiêu P95 | Đạt",
  );
  for (const [key, g] of Object.entries(GROUPS)) {
    const s = [...samples[key]].sort((a, b) => a - b);
    if (!s.length) continue;
    const r = {
      requests: s.length,
      errors: errors[key],
      rejected: rejected[key],
      avg: s.reduce((a, b) => a + b, 0) / s.length,
      p50: percentile(s, 50),
      p95: percentile(s, 95),
      p99: percentile(s, 99),
      max: s[s.length - 1],
      limit: g.limit,
    };
    r.pass = r.p95 < g.limit && r.errors === 0;
    ok &&= r.pass;
    result[key] = r;
    const f = (x) => x.toFixed(0);
    console.log(
      `${g.label} | ${r.requests} | ${r.errors} | ${r.rejected} | ${f(r.avg)} | ${f(r.p50)} | ${f(r.p95)} | ${f(r.p99)} | ${f(r.max)} | < ${g.limit} | ${r.pass ? "Đạt" : "KHÔNG"}`,
    );
  }
  const total = Object.values(result).reduce((a, r) => a + r.requests, 0);
  console.log(
    `\nTổng ${total} yêu cầu trong ${seconds.toFixed(0)} s (${(total / seconds).toFixed(1)} yêu cầu/giây).`,
  );
  for (const [k, n] of errorDetails) console.log(`  Lỗi ${n} lần: ${k}`);
  if (args.out) {
    const { writeFileSync } = await import("node:fs");
    writeFileSync(
      args.out,
      JSON.stringify(
        {
          base: BASE,
          users: USERS,
          durationS: DURATION_S,
          readOnly: READ_ONLY,
          seconds,
          result,
        },
        null,
        2,
      ),
    );
  }
  process.exitCode = ok ? 0 : 1;
}

main().catch((e) => {
  console.error(e.message);
  process.exitCode = 2;
});
