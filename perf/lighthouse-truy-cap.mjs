// Đo điểm Accessibility của Lighthouse (KPI K8: >= 90 mỗi trang) trên 6 trang, kể cả trang cần
// đăng nhập (giỏ hàng, thanh toán). Tự tạo 1 khách @tai.test, thêm 1 sản phẩm vào giỏ và 1 địa chỉ.
//
//   cd perf
//   npm i --no-save lighthouse@13.6.0 playwright-core@1.56.1      (1 lần, không ghi vào repo)
//   node lighthouse-truy-cap.mjs --web http://localhost:3000 --api http://localhost:8080
//
// Dùng Google Chrome đã cài trên máy (hoặc đặt CHROME_PATH tới file chrome). Backend phải cho
// phép origin của --web trong CORS_ALLOWED_ORIGINS, nếu không trang sẽ hiện lỗi tải dữ liệu.
import { rmSync } from "node:fs";
import lighthouse from "lighthouse";
import { chromium } from "playwright-core";

const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : fallback;
};
const WEB = arg("web", "http://localhost:3000").replace(/\/$/, "");
const API = arg("api", "http://localhost:8080").replace(/\/$/, "");
const PORT = 9222;
const PROFILE = ".chrome-profile";

async function api(method, path, body, token) {
  const res = await fetch(API + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status}`);
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// Khách thử có giỏ và địa chỉ để trang thanh toán hiện đủ nội dung.
const run = Date.now().toString(36);
const { accessToken: token } = await api("POST", "/api/auth/register", {
  email: `lh-${run}@tai.test`,
  password: `LhThu${run}1`,
  fullName: "Khách Lighthouse",
});
let productId;
for (const card of (await api("GET", "/api/products?size=40")).content) {
  const product = await api("GET", `/api/products/${card.id}`);
  const variant = product.variants.find((v) => v.stock > 2);
  if (variant) {
    productId = product.id;
    await api(
      "POST",
      "/api/cart/items",
      { variantId: variant.id, quantity: 1 },
      token,
    );
    break;
  }
}
await api(
  "POST",
  "/api/addresses",
  {
    recipientName: "Khách Lighthouse",
    phone: "0901234567",
    addressDetail: "1 Võ Văn Ngân, Thủ Đức",
    isDefault: true,
  },
  token,
);

// Lighthouse mở tab mới trong ngữ cảnh mặc định của trình duyệt: dùng profile riêng (persistent)
// để token đặt vào localStorage ở đây cũng có trong các tab đó.
rmSync(PROFILE, { recursive: true, force: true });
const browser = await chromium.launchPersistentContext(PROFILE, {
  headless: true,
  ...(process.env.CHROME_PATH
    ? { executablePath: process.env.CHROME_PATH }
    : { channel: "chrome" }),
  args: [`--remote-debugging-port=${PORT}`],
});
const page = browser.pages()[0] ?? (await browser.newPage());
await page.goto(`${WEB}/login`);
await page.evaluate((t) => {
  localStorage.setItem("fashionshop.token", t);
  localStorage.setItem(
    "fashionshop.tokenExpiresAt",
    String(Date.now() + 86_400_000),
  );
}, token);

const pages = [
  ["Trang chủ", "/"],
  ["Danh sách sản phẩm", "/products"],
  ["Chi tiết sản phẩm", `/products/${productId}`],
  ["Giỏ hàng", "/cart"],
  ["Thanh toán", "/checkout"],
  ["Đăng nhập (chưa đăng nhập)", "/login", true],
];
let ok = true;
for (const [name, path, loggedOut] of pages) {
  if (loggedOut) await page.evaluate(() => localStorage.clear());
  const { lhr } = await lighthouse(`${WEB}${path}`, {
    port: PORT,
    logLevel: "error",
    onlyCategories: ["accessibility"],
    disableStorageReset: true,
    formFactor: "desktop",
    screenEmulation: { disabled: true },
  });
  const score = Math.round(lhr.categories.accessibility.score * 100);
  const failed = Object.values(lhr.audits)
    .filter((a) => a.score === 0 && a.scoreDisplayMode === "binary")
    .map((a) => a.id);
  ok &&= score >= 90;
  console.log(
    `${name.padEnd(28)} ${String(score).padStart(3)}  ${failed.join(", ")}`,
  );
}
await browser.close();
rmSync(PROFILE, { recursive: true, force: true });
process.exitCode = ok ? 0 : 1;
