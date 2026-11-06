# TEST_PLAN_AND_CASES: KẾ HOẠCH KIỂM THỬ VÀ CA KIỂM THỬ

> Phục vụ TC2.5 (rubric G5).
>
> **Cập nhật:** 06/11/2026, đối chiếu với nhánh `main` tại commit `c415b14` (merge PR #135).
>
> **Nguồn:**
> - mã test trong repository;
> - log GitHub Actions của lần chạy CI trên commit đó;
> - GitHub Issues (nhãn `bug`);
> - `perf/README.md` (đo tải, Lighthouse);
> - `docs/dac-ta-yeu-cau.md` (user story, tiêu chí chấp nhận, business rule).
>
> Mọi số liệu trong file kiểm tra lại được bằng cách chạy lệnh ở Mục 3.

## 1. Phạm vi và mục tiêu

**Đối tượng kiểm thử:** 3 service của hệ thống:
- `backend` (Spring Boot);
- `frontend` (React);
- `ml-service` (FastAPI: gợi ý, phân nhóm, hàng đợi tính lại).

Phạm vi nghiệp vụ là 26 user story trong đặc tả: 22 story gốc và US-23 đến US-26 bổ sung sau (thử nghiệm A/B, hồ sơ cá nhân và quên mật khẩu là phần mở rộng của US-13, US-04, US-05). Cùng với đó là các business rule BR-01 đến BR-49.

**Ngoài phạm vi:**
- Cổng VNPay thật: chỉ dùng sandbox.
- Thiết bị di động thật và trình duyệt ngoài Chromium.
- Đợt thực nghiệm với người dùng thật (SUS, A/B): chưa thực hiện, xem Mục 9.

**Mục tiêu:**
1. Mỗi tiêu chí chấp nhận (AC) quan trọng của các user story có ít nhất 1 test tự động. Bảng truy vết ở Mục 6 có đủ ca dương (+), ca âm (−) và ca biên (B).
2. 100% test pass trên CI trước khi merge vào `main`. Không merge khi còn job đỏ.
3. Không còn lỗi mức Critical đang mở khi nộp; mọi lỗi tìm được đều có issue và test hồi quy.
4. Các chỉ tiêu phi chức năng ở Bảng 2.9 của báo cáo (P95, khả năng truy cập) được đo, có số liệu.

## 2. Chiến lược: 4 tầng tự động và kiểm thử phi chức năng

Số liệu tính trên commit `c415b14`, ngày 06/11/2026.

| Tầng | Công cụ | Vị trí mã test | Kiểm tra gì | Số test |
|---|---|---|---|---|
| **Unit** | JUnit 5, Mockito | `backend/src/test/.../service`, `security`, `entity`, `dto`, `repository`, `client` | Quy tắc thuần, không cần CSDL, ví dụ: <br>• khóa đăng nhập 5 lần / 15 phút; <br>• JWT; <br>• so khớp biến thể không dấu; <br>• kiểm định z của A/B | 12 lớp |
| | pytest | `ml-service/tests/test_recommendation_model.py`, `test_segmentation_model.py`, `test_segmentation_features.py`, `test_gender.py`, `test_build_tiki_catalog.py`, `test_simulation.py` | • Vector sản phẩm, điểm gợi ý, ưu tiên tương tác gần đây <br>• K-Means, chọn K, đặt tên nhóm <br>• Dựng danh mục mẫu, dữ liệu mô phỏng | 84 |
| | Vitest + React Testing Library + jsdom | `frontend/tests/` (components, hooks, context, services, pages) | Từng trang và component với API giả: <br>• hiển thị, thông báo lỗi <br>• chuyển trang theo quyền <br>• giỏ hàng của khách vãng lai, lưu token | 409 (53 file) |
| **Integration** | Spring Boot Test + MySQL 8.4 thật (CSDL riêng `fashionshop_test`) | `backend/src/test/.../controller/*IntegrationTest.java` | Đi trọn controller, service, repository và CSDL, kể cả: <br>• khóa dòng (`SELECT … FOR UPDATE`) <br>• giao dịch và thử lại khi deadlock <br>• Flyway migration | 19 lớp |
| | pytest + MySQL thật (CSDL `fashionshop_mltest`, dựng lại từ các file migration của backend) | `ml-service/tests/test_recommendation_db.py`, `test_segmentation_db.py`, `test_recompute_worker.py`, `test_recompute_queue.py` | Tiến trình lấy việc từ `recompute_request`: <br>• ghi gợi ý và nhóm trong cùng giao dịch <br>• thử lại khi lỗi <br>• khóa 1 tiến trình mỗi CSDL <br>• việc hằng đêm | 45 |
| **API** | MockMvc gọi REST API có JWT thật | cùng các lớp `*IntegrationTest` ở trên | • Mã trạng thái HTTP <br>• định dạng lỗi ProblemDetail <br>• phân quyền: không token trả 401, sai vai trò trả 403 <br>• kiểm tra dữ liệu đầu vào trả 400, xung đột trả 409 | (tính trong Integration) |
| | Postman | `docs/postman/fashion-shop.postman_collection.json` | Chạy tay khi phát triển, các API chia theo nhóm (công khai, khách hàng, admin, nội bộ ml-service) | thủ công |
| | pytest + TestClient | `ml-service/tests/test_health.py`, `test_recommendation_db.py::test_*_api` | API nội bộ của ml-service | (tính ở trên) |
| **E2E** | Playwright (Chromium thật) trên cả cụm Docker Compose 4 container | `frontend/e2e/*.spec.js` | 4 luồng chính qua giao diện thật: <br>• đăng ký, đăng nhập <br>• gộp giỏ khi đăng nhập <br>• đặt đơn COD <br>• admin chuyển trạng thái đơn | 6 |

**Tổng số test:**
- Backend: 200 phương thức test (3 phương thức dạng tham số), tổng 220 lần chạy.
- ml-service: 159 test.
- Frontend: 409 test Vitest và 6 test E2E.

**Kiểm thử phi chức năng:**

| Loại | Cách làm | Ở đâu |
|---|---|---|
| Hiệu năng (P95, 50 người đồng thời) | Script `perf/tai-dong-thoi.mjs` tạo 50 khách ảo, đo P95 theo 4 nhóm thao tác | `perf/README.md` |
| Khả năng truy cập (KPI K8) | Lighthouse Accessibility trên 6 trang | `perf/lighthouse-truy-cap.mjs` |
| Thời gian tính lại gợi ý khi có 100.000 khách | Đo thời gian xử lý mỗi khách rồi nhân lên, so với yêu cầu < 30 phút | issue #58, PR #60 |
| Bảo mật | Theo 3 hướng: <br>• gitleaks quét toàn bộ lịch sử Git ở mỗi PR <br>• SonarQube Cloud phân tích tĩnh (bảo mật, độ tin cậy, mã trùng) <br>• test phân quyền và chống dò mật khẩu (Mục 6) | CI |
| Chất lượng mô hình học máy | Đánh giá ngoại tuyến: giấu đơn cuối của mỗi khách, đo tỷ lệ trúng, Precision, Recall, NDCG ở 10 gợi ý đầu. Phân nhóm đo bằng Silhouette và ARI | `ml-service`: `python -m app.recommendation.evaluate`, `python -m app.segmentation.evaluate` |

## 3. Môi trường và cách chạy

| Môi trường | Dùng cho | Ghi chú |
|---|---|---|
| CI (GitHub Actions, `ubuntu-latest`) | Mọi tầng tự động | Mỗi job có MySQL 8.4 tạm. Job "Docker images" dựng 3 image, chạy cả cụm bằng `docker compose up -d --wait`, rồi chạy Playwright |
| Máy phát triển | Chạy test trước khi mở PR, đo tải, Lighthouse | Backend dùng CSDL riêng `fashionshop_test`, ml-service dùng `fashionshop_mltest`, không đụng dữ liệu dev |
| Staging (Azure, `https://fashionshop-staging.eastasia.cloudapp.azure.com`) | Kiểm thử thủ công (Mục 7), thanh toán VNPay sandbox thật | Deploy tự động sau mỗi commit vào `main`, kiểm tra health trả UP |

**Dữ liệu test:**
- Mỗi test integration tự tạo dữ liệu và tự xóa sau khi chạy.
- Test E2E tạo khách mới có đuôi `@e2e.test` qua REST API.
- Đánh giá học máy dùng dữ liệu mô phỏng với seed 2026, 2027, 2028 (`python -m app.simulation`).

**Lệnh chạy** (giống trong CI):

```
cd backend   && ./mvnw verify            # build + test + Checkstyle + JaCoCo (cần MySQL)
cd frontend  && npm run test:coverage    # Vitest + độ phủ
cd frontend  && npm run test:e2e         # Playwright, cần cả cụm đang chạy
cd ml-service && uv run pytest           # pytest + pytest-cov (cần MySQL)
node perf/tai-dong-thoi.mjs --users 50 --duration 180
```

## 4. Tiêu chí bắt đầu và kết thúc

| Mốc | Tiêu chí |
|---|---|
| Bắt đầu kiểm thử một thay đổi | • Có nhánh và PR riêng. <br>• Code đã qua bộ định dạng và lint: Spotless/Checkstyle, Prettier/ESLint, Ruff. <br>• Mọi API mới đã có trong Postman collection. |
| Được merge một PR | • Đủ 6 job CI bắt buộc xanh: Backend, Frontend, ML service, Docker images, SonarQube Cloud, gitleaks. <br>• SonarQube 0 issue mới. <br>• Độ phủ code mới ≥ 80% là mục tiêu: cổng chất lượng mới chỉ để báo cáo, PR thấp hơn phải ghi lý do. <br>• Lỗi tìm được trong PR đã có issue. |
| Hoàn tất một lỗi | • Issue mở theo mẫu "Báo lỗi" (mức độ, thành phần, ai phát hiện). <br>• PR sửa ghi `Fixes #n`. <br>• Có test hồi quy tái hiện lỗi, trừ lỗi cấu hình hay hạ tầng không viết test được. Các lỗi này ghi cách kiểm tra tay trong issue. |
| Kết thúc kiểm thử trước khi nộp | • 100% test pass trên `main`. <br>• Không còn issue lỗi đang mở. <br>• Không có lỗi Critical. <br>• P95 và Lighthouse đạt chỉ tiêu. <br>• Các ca thủ công ở Mục 7 đã chạy lại trên bản cuối. |

## 5. Quản lý lỗi

**Công cụ:** GitHub Issues của repository làm bug tracker, nhãn `bug`, theo mẫu `.github/ISSUE_TEMPLATE/bao-loi.yml`. Mẫu có các trường: mô tả, mức độ, thành phần, ai phát hiện, cách sửa.

**Danh sách lỗi:** https://github.com/ngocmanhnct/fashion-recommender-shop/issues?q=label%3Abug

**Số liệu đến 06/11/2026:** có 57 issue lỗi (#25 đến #137), **đã đóng cả 57**.

Theo mức độ ghi trong issue:

| Mức độ | Số issue |
|---|---:|
| Critical (#94: gửi song song nhiều lần đăng nhập sai vượt được giới hạn 5 lần, đã sửa ở PR #107 kèm test `LoginAttemptServiceTest.parallelAttemptsCannotTryMoreThanFivePasswords`) | 1 |
| Major hoặc Cao | 17 |
| Trung bình | 3 |
| Minor hoặc Thấp | 33 |
| Trivial | 3 |

**Nguồn phát hiện:**
- SonarQube Cloud;
- CI: test đỏ, test E2E bắt được issue #63;
- tự test và dùng thử;
- buổi tổng duyệt 01–03/11: issue #93–#106;
- trợ giảng khi xem demo: issue #65.

Phân tích nguyên nhân các lỗi do AI sinh ra nằm ở `AI_LOG.MD` Mục 2.

## 6. Ca kiểm thử và bảng truy vết tới tiêu chí chấp nhận

**Quy ước:**
- Cột "Loại": **+** là ca dương (luồng đúng), **−** là ca âm (đầu vào sai, sai quyền, vi phạm quy tắc), **B** là ca biên (đúng ngưỡng).
- Cột "Tầng": U = unit, I = integration và API (MockMvc), F = test giao diện Vitest, E = E2E.

**Tên test:**
- Backend ghi `Lớp.phươngThức`, lớp nằm trong `backend/src/test/java/com/manhdn/fashionshop/`.
- ml-service ghi `file::hàm`, file nằm trong `ml-service/tests/`.
- Frontend ghi tên file trong `frontend/tests/` hoặc `frontend/e2e/`.

**Kết quả:** tất cả ca dưới đây **Pass** trên commit `c415b14`, lần chạy CI ngày 06/11/2026.

### 6.1 Duyệt và tìm sản phẩm (US-01, US-02, US-03)

| Mã | US / BR | Tiêu chí chấp nhận (tóm tắt) | Loại | Tầng | Test tự động |
|---|---|---|---|---|---|
| TC-01 | US-01 | Khách chưa đăng nhập xem được sản phẩm đang bán; sản phẩm đã xóa mềm bị ẩn | + / − | I | `ProductCatalogIntegrationTest.anonymousSeesProductsForSaleAndDeletedOnesAreHidden` |
| TC-02 | US-01 | Lọc theo danh mục, thương hiệu, khoảng giá; sắp xếp theo bán chạy, mới nhất, giá; phân trang | + | I, F | `ProductCatalogIntegrationTest.filtersByCategoryBrandAndVariantPriceRange`, `.sortsByPopularityNewestAndPrice`, `.pagesTheResults`; `pages/ProductsPage.test.jsx` |
| TC-03 | US-01 | Tham số lọc sai (trang âm, giá ngược) trả 400 | − | I | `ProductCatalogIntegrationTest.invalidParametersGive400` |
| TC-04 | US-02 | Từ khóa khớp đầu từ, không phân biệt hoa thường và dấu ("giay" khớp "Giày", "áo" không khớp "Cao") | + / B | I, U | `ProductCatalogIntegrationTest.keywordIgnoresCaseAndAccentsButOnlyMatchesWordStarts`, `.keywordMustMatchEveryTokenInNameCategoryOrBrand`; `ProductSpecificationsTest.*` |
| TC-05 | US-02 | Ký tự `%` và `_` trong từ khóa không thành ký tự đại diện; chữ tách dấu (NFD) vẫn tìm ra | − / B | I | `ProductCatalogIntegrationTest.percentAndUnderscoreAreNotWildcards`, `.decomposedVietnameseNamesAreStoredComposedSoKeywordsMatchThem` |
| TC-06 | US-02 | Gợi ý tìm kiếm ưu tiên sản phẩm khớp nhiều từ, bỏ sản phẩm hết hàng; không có thì lấp bằng bán chạy | + | I | `ProductCatalogIntegrationTest.suggestionsPreferProductsMatchingMoreTokensAndSkipSoldOutOnes`, `.suggestionsFallBackToInStockBestSellers` |
| TC-07 | US-03, BR-19 | Ghi lượt xem chỉ cho khách đã đăng nhập; không token trả 401, admin trả 403 | + / − | I | `ProductCatalogIntegrationTest.recordingViewsNeedsCustomerRole`; `ProductViewIntegrationTest.viewIsLoggedAndRequestsRecomputeInThirtySecondsUtc` |
| TC-08 | BR-19 | Sản phẩm thứ 5 khác nhau trong 30 ngày là ngưỡng "hành động rõ ý"; xem lại không tính; lượt xem quá 30 ngày không tính | B | I | `ProductViewIntegrationTest.fifthDistinctProductInThirtyDaysIsAStrongAction`, `.repeatViewNeverCountsAsReachingTheThreshold`, `.viewsOlderThanThirtyDaysAreNotCounted` |
| TC-09 | US-03 | Sản phẩm đã xóa hoặc không tồn tại trả 404 và không ghi gì | − | I | `ProductViewIntegrationTest.deletedProductOrMissingAccountGives404AndWritesNothing` |
| TC-10 | US-03 | Trang chi tiết: chọn phân loại, ảnh minh họa theo danh mục, nút thêm giỏ | + | F | `pages/ProductDetailPage.test.jsx`, `components/ProductImage.test.jsx`, `services/variantSelection.test.js`, `services/categoryPhotos.test.js` |

### 6.2 Tài khoản (US-04, US-05)

| Mã | US / BR | Tiêu chí chấp nhận (tóm tắt) | Loại | Tầng | Test tự động |
|---|---|---|---|---|---|
| TC-11 | US-04, BR-04 | Đăng ký tạo tài khoản Khách hàng và trả token; trường `role` client gửi lên bị bỏ qua | + / − | I, U | `AuthControllerIntegrationTest.registerCreatesCustomerAndReturnsToken`, `.roleSentByClientIsIgnored`; `AuthServiceTest.registerCreatesCustomerWithNormalizedEmailAndHashedPassword` |
| TC-12 | US-04, BR-02 | Mật khẩu yếu, email sai định dạng trả 400 | − | I, F | `AuthControllerIntegrationTest.weakPasswordAndBadEmailAreRejected`; `pages/AuthPages.test.jsx` |
| TC-13 | US-04, BR-01 | Email trùng (không phân biệt hoa thường) bị từ chối, kể cả khi 2 yêu cầu đến cùng lúc (ràng buộc UNIQUE) | − | I, U | `AuthControllerIntegrationTest.duplicateEmailIsRejectedCaseInsensitive`; `AuthServiceTest.registerTurnsUniqueViolationIntoEmailExists` |
| TC-14 | US-05 | Đăng nhập trả token mở được `/api/auth/me`; sai mật khẩu và email không tồn tại báo giống nhau | + / − | I, E | `AuthControllerIntegrationTest.loginReturnsTokenThatOpensMe`, `.wrongPasswordAndUnknownEmailLookTheSame`; `e2e/auth.spec.js` (2 ca) |
| TC-15 | US-05 | Sai 4 lần chưa khóa; sai lần thứ 5 trong 15 phút thì khóa, kể cả mật khẩu đúng; hết 15 phút thì mở | B | U, I | `LoginAttemptServiceTest.fourFailuresDoNotLock`, `.fifthFailureWithinWindowLocks`, `.lockExpiresAfterFifteenMinutes`; `AuthControllerIntegrationTest.fiveFailuresLockEvenTheCorrectPassword` |
| TC-16 | US-05 (Critical #94) | Gửi song song nhiều lần sai cũng không thử được quá 5 mật khẩu | − / B | U | `LoginAttemptServiceTest.parallelAttemptsCannotTryMoreThanFivePasswords` |
| TC-17 | Bảo mật | Không token hoặc token sai trả 401; API admin trả 403 cho khách; `/actuator/health` công khai | − | I, U | `AuthControllerIntegrationTest.requestsWithoutValidTokenGet401`, `.adminApiIsForbiddenForCustomerAndOpenForAdmin`, `.healthStaysPublic`; `JwtServiceTest.expiredTokenIsRejected`, `.tokenSignedWithOtherKeyIsRejected`, `.secretShorterThan32BytesIsRefused` |
| TC-18 | US-04, BR-48 | Sửa hồ sơ; giá trị sai bị từ chối; mật khẩu có dấu quá 72 byte trả 400 (không phải 500) | + / − / B | I, F | `AccountIntegrationTest.customerUpdatesProfileAndInvalidValuesAreRejected`, `.changePasswordNeedsCurrentPasswordAndLocksAfterFiveWrongTries`; `pages/ProfilePage.test.jsx` |
| TC-19 | US-05, BR-47 | Quên mật khẩu: gửi liên kết dùng 1 lần, CSDL chỉ lưu mã băm; email lạ nhận cùng câu trả lời; gửi lại bị giới hạn | + / − | I, F | `AccountIntegrationTest.forgotPasswordEmailsSingleUseLinkAndStoresOnlyItsHash`, `.unknownEmailGetsSameAnswerAndResendIsLimited`; `pages/PasswordResetPages.test.jsx` |
| TC-20 | US-05, BR-47 | Liên kết hết hạn 30 phút hoặc cũ hơn lần đổi mật khẩu bị từ chối; đặt lại mở khóa đăng nhập | − / B | I | `AccountIntegrationTest.expiredLinkAndLinksOlderThanAPasswordChangeAreRejected`, `.resetUnlocksLoginLockedByWrongPasswords` |
| TC-21 | US-05 | Token hết hạn hoặc đăng xuất ở tab khác thì giao diện đăng xuất; trang cần quyền chuyển tới đăng nhập | − | F, E | `context/AuthProvider.test.jsx`, `components/RequireRole.test.jsx`, `services/tokenStorage.test.js`; `e2e/auth.spec.js` (khách chưa đăng nhập vào trang đơn hàng) |

### 6.3 Giỏ hàng (US-06, US-07, US-08)

| Mã | US / BR | Tiêu chí chấp nhận (tóm tắt) | Loại | Tầng | Test tự động |
|---|---|---|---|---|---|
| TC-22 | US-06 | Thêm cùng biến thể 2 lần thì cộng số lượng, ghi sự kiện và yêu cầu tính lại gợi ý | + | I | `CartIntegrationTest.addingSameVariantTwiceSumsQuantityLogsEventAndRequestsRecompute` |
| TC-23 | US-06, BR-08 | Thêm nhiều hơn tồn kho, hết hàng hoặc sản phẩm đã xóa bị từ chối | − / B | I, F | `CartIntegrationTest.addingMoreThanStockOrOutOfStockOrDeletedIsRejected`; `components/AddToCart.test.jsx`, `components/QuantityStepper.test.jsx` |
| TC-24 | US-07, BR-11 | Đọc giỏ tự loại dòng ngừng bán hoặc hết hàng, giảm dòng vượt tồn kho, có thông báo | + / − | I, F | `CartIntegrationTest.readingCartRemovesUnavailableLinesAndReducesQuantityWithNotices`, `.updateAndRemoveLine`; `pages/CartPage.test.jsx` |
| TC-25 | US-06, BR-06, BR-07 | Giỏ khách vãng lai lưu ở trình duyệt; khi đăng nhập thì gộp vào giỏ tài khoản, cộng dồn tối đa bằng tồn kho | + / B | I, F, E | `CartIntegrationTest.mergeSumsSameVariantCapsAtStockAndSkipsUnavailable`, `.previewIsPublicAndWritesNothing`; `context/CartProvider.test.jsx`, `services/guestCart.test.js`; `e2e/cart.spec.js` |
| TC-26 | US-08 | Giỏ trên server chỉ cho khách đã đăng nhập; bấm mua khi chưa đăng nhập thì chuyển tới đăng nhập | − | I, E | `CartIntegrationTest.cartNeedsCustomerToken`; `e2e/cart.spec.js` |
| TC-27 | US-07, BR-49 | "Thường được mua cùng": giữ thứ tự của ml-service, bỏ sản phẩm đang trong giỏ hoặc hết hàng; ml-service tắt thì đếm đơn chung, không lấp bán chạy; cần 1 đến 50 sản phẩm | + / − / B | I | `RecommendationIntegrationTest.boughtTogetherKeepsMlOrderAndSkipsCartAndSoldOutProducts`, `.withoutMlServiceBoughtTogetherCountsSharedOrdersAndDoesNotFill`, `.boughtTogetherNeedsOneToFiftyProducts` |

### 6.4 Đặt hàng và thanh toán (US-09, US-10, US-11, US-20)

| Mã | US / BR | Tiêu chí chấp nhận (tóm tắt) | Loại | Tầng | Test tự động |
|---|---|---|---|---|---|
| TC-28 | US-10, BR-15, BR-29, BR-10 | Đặt COD: lưu bản sao giỏ và địa chỉ, trừ tồn kho, xóa giỏ; gửi lại cùng idempotency key trả đúng đơn cũ | + | I, E | `OrderIntegrationTest.placingCodOrderSnapshotsCartDeductsStockClearsCartAndIsIdempotent`; `e2e/order.spec.js` |
| TC-29 | BR-35 | Phí ship 30.000đ, miễn phí từ đúng 500.000đ | B | I | `OrderIntegrationTest.subtotalFromFiveHundredThousandShipsFree` |
| TC-30 | BR-09 | Thiếu tồn kho hoặc giá đổi so với lúc xem trước thì từ chối cả đơn (409) | − | I, F | `OrderIntegrationTest.notEnoughStockOrChangedPriceRejectsWholeOrder`, `.invalidOrdersAreRejected`; `pages/CheckoutPage.test.jsx` |
| TC-31 | BR-08 | 2 khách cùng mua chiếc cuối cùng thì chỉ 1 đơn thành công | B | I | `OrderIntegrationTest.twoCustomersBuyingTheLastItemAtOnceSellOnlyOne` |
| TC-32 | US-20, BR-13, BR-16 | Khách tự hủy đơn Chờ xử lý thì hoàn kho; đơn đã Đang giao không hủy được | + / − | I | `OrderIntegrationTest.customerCancelsPendingOrderAndStockReturnsButNotOnceShipped` |
| TC-33 | US-09 | Sổ địa chỉ luôn đúng 1 địa chỉ mặc định, tối đa 10; API cần token khách | + / − | I | `OrderIntegrationTest.addressBookKeepsExactlyOneDefault`, `.ordersAndAddressesNeedCustomerToken` |
| TC-34 | US-10, BR-30 | VNPay: chỉ callback có chữ ký đúng mới chuyển đơn sang Đã thanh toán, gọi lại nhiều lần vẫn đúng 1 lần | + / − | I | `PaymentIntegrationTest.onlineOrderIsPaidThroughSignedVnPayCallbacksOnlyOnce` |
| TC-35 | BR-14, BR-34 | Hủy đơn đã thanh toán thì hoàn tiền 1 lần, lỗi thì thử lại tối đa 3 lần rồi chờ xử lý tay; đơn ONLINE chưa trả quá 30 phút tự hủy, tiền về muộn được hoàn | − / B | I | `PaymentIntegrationTest.cancellingPaidOrderRefundsOnceRetriesThenNeedsManualHandling`, `.unpaidOnlineOrderIsCancelledAfterThirtyMinutesAndLatePaymentIsRefunded` |
| TC-36 | BR-16 (#93) | Đọc lại đơn sau khi chờ khóa thấy bản mới nhất, không hoàn kho 2 lần | B | I | `PaymentIntegrationTest.lockedReadSeesOrderChangedByAnotherTransactionAfterSnapshot` |
| TC-37 | US-11 | Danh sách và chi tiết đơn của tôi | + | F | `pages/OrderDetailPage.test.jsx`, `services/orderApi.test.js`, `services/orderLabels.test.js` |

### 6.5 Gợi ý sản phẩm (US-12, US-13, US-23)

| Mã | US / BR | Tiêu chí chấp nhận (tóm tắt) | Loại | Tầng | Test tự động |
|---|---|---|---|---|---|
| TC-38 | US-12 | Sản phẩm liên quan theo cosine thuộc tính; giữ thứ tự và lý do của ml-service, thiếu thì lấp bán chạy (BR-22) | + | I, U | `RecommendationIntegrationTest.relatedKeepsMlOrderAndReasonsThenFillsWithBestSellers`; `test_recommendation_model.py::test_related_products`, `::test_vectors_have_unit_length_and_similar_products_are_closer` |
| TC-39 | US-12, BR-22 | ml-service lỗi, chậm quá 500 ms hoặc tắt thì dùng quy tắc, trang vẫn có gợi ý | − | U, I | `MlServiceClientTest.errorsAndSlowAnswersGiveNothing`, `.unreachableOrDisabledServiceGivesNothing`; `RecommendationIntegrationTest.withoutMlServiceRelatedUsesRules` |
| TC-40 | US-13, BR-19 | Trang chủ đọc danh sách đã tính theo thứ tự, bỏ sản phẩm hết hàng; chưa đủ hành vi thì theo khảo sát; không có gì thì bán chạy | + / B | I, F | `RecommendationIntegrationTest.homeReadsStoredListInRankOrderAndSkipsUnavailableProducts`, `.listWithoutEnoughBehaviourComesFromSurveyAndNoListMeansBestSellers`; `components/PersonalRecommendations.test.jsx` |
| TC-41 | US-13 (#65) | Hàng "Vì bạn vừa xem / vừa thêm giỏ" lấy hành động mới nhất; tương tác mới nặng hơn tương tác cũ (bán rã 1 ngày) | + / B | I, U | `RecommendationIntegrationTest.homeAddsRowForLatestViewWithoutCartItemsAndKeepsItOutOfMainList`, `.latestCartItemWinsOverOlderViewAndOldActivityIsIgnored`; `test_recommendation_model.py::test_recent_interaction_outweighs_older_ones`, `::test_recency_factor_halves_each_half_life` |
| TC-42 | US-13 | Không gợi ý sản phẩm đã mua, đang trong giỏ hoặc hết hàng; đa dạng danh mục | − | U | `test_recommendation_model.py::test_behaviour_excludes_bought_cart_and_out_of_stock`, `::test_diversity_spreads_categories` |
| TC-43 | BR-20 | Hành động mới ghi yêu cầu tính lại; ml-service tính xong ghi danh sách và xóa yêu cầu trong cùng giao dịch; lỗi thì rollback và hẹn lại | + / − | I | `test_recompute_worker.py::test_handler_writes_commit_together_with_delete`, `::test_failed_handler_rolls_back_and_schedules_retry`, `::test_request_arriving_during_computation_is_computed_once_more`; `ProductViewIntegrationTest.requestsOfOneTransactionAreMergedKeepingTheShortestDelay`, `.rolledBackTransactionWritesNoRequest` |
| TC-44 | BR-20 | Chỉ 1 tiến trình lấy việc trên 1 CSDL; lượt đủ lô thì lấy tiếp ngay (100.000 khách khoảng 29 phút) | B | I | `test_recompute_worker.py::test_only_one_worker_holds_the_lock`, `::test_lock_is_per_database`, `::test_full_batch_is_followed_by_next_batch_without_waiting` |
| TC-45 | BR-08 | Deadlock hoặc chờ khóa quá hạn thì chạy lại cả giao dịch, tối đa 3 lần | B | I | `ProductViewIntegrationTest.transactionRetryRunsTheWholeTransactionAgainAfterADeadlock`, `.transactionRetryAlsoRetriesALockWaitTimeout`, `.transactionRetryGivesUpAfterThreeAttemptsAndNeverRetriesOtherErrors` |
| TC-46 | US-23, BR-37, BR-41 | Cây khảo sát công khai, rẽ nhánh; câu trả lời phải đi đúng cây | + / − | I, F | `SurveyIntegrationTest.surveyTreeIsPublicAndBranches`, `.answersMustFollowTheTree`; `pages/SurveyPage.test.jsx` |
| TC-47 | US-23, BR-42 (#124) | Chọn đồ nữ không bao giờ ra sản phẩm nam, kể cả khi lấp bán chạy; dịp sử dụng thu hẹp nhưng không bỏ loại đồ đã chọn | − / B | I, U | `SurveyIntegrationTest.womenNeverGetMenProductsEvenWhenFillingWithBestSellers`, `.occasionNarrowsEachChosenItemTypeButNeverDropsOne`; `test_recommendation_model.py::test_survey_only_suggests_the_chosen_gender`; `test_gender.py::*` |
| TC-48 | US-23 | Kiểu tiết kiệm nới ngân sách 30% và xếp theo giá; kiểu thương hiệu đưa thương hiệu đã chọn lên đầu; trong ngân sách đứng trước | + / B | I, U | `SurveyIntegrationTest.saverPathKeepsMenCategoriesStretchesBudgetAndSortsByPrice`, `.brandPathOffersBrandsOfChosenCategoriesAndPutsChosenBrandFirst`; `test_recommendation_model.py::test_survey_keeps_to_the_budget_then_fills_from_outside` |
| TC-49 | US-23, BR-38 | Nhánh tìm quà chỉ xem kết quả, không lưu; lưu khảo sát thì ghi sở thích và yêu cầu tính lại; bỏ qua thì không hỏi lại | + / − | I | `SurveyIntegrationTest.giftPathIsEvaluatedButNeverSaved`, `.savingStoresPreferencesRequestsRecomputeAndRedoReplacesThem`, `.skippingIsRememberedSoTheSurveyDoesNotShowAgain`, `.ownSurveyNeedsCustomerToken` |
| TC-50 | US-13 (CTR) | Ghi lượt hiện, lượt bấm theo vị trí và nguồn; khách vãng lai ghi không tài khoản; admin bị bỏ qua; dữ liệu sai bị từ chối | + / − | I, F | `RecommendationEventIntegrationTest.*` (6 ca); `components/RecommendationGrid.test.jsx` |
| TC-51 | US-13, BR-46 | A/B: chia nhóm theo băm cố định; nhóm đối chứng thấy bán chạy (bỏ đã mua, đang trong giỏ); kết quả đếm theo nhóm; dưới 30 khách mỗi nhóm thì chưa kết luận | + / − / B | I, U | `AbTestIntegrationTest.*` (4 ca); `AbTestServiceTest.verdictsFollowSampleSizeAndSignificance`; `TwoProportionTestTest.*` |

### 6.6 Phân nhóm khách hàng và ưu đãi (US-18, US-19, US-21, US-22, US-24, US-25)

| Mã | US / BR | Tiêu chí chấp nhận (tóm tắt) | Loại | Tầng | Test tự động |
|---|---|---|---|---|---|
| TC-52 | US-19, BR-23 | R, F, M chỉ tính đơn Đã giao của khách; K-Means tìm và đặt tên đúng các nhóm rõ; kết quả lặp lại được | + | U, I | `test_segmentation_db.py::test_load_rfm_counts_only_delivered_orders_of_customers`; `test_segmentation_model.py::test_train_finds_and_names_clear_groups`, `::test_train_is_repeatable`, `::test_elbow_point_and_choose_k` |
| TC-53 | BR-23, BR-24 | Ít hơn 20 khách thì không huấn luyện; khách mới gán vào tâm gần nhất, không huấn luyện lại; khách không còn đơn thì bỏ nhóm | − / B | U, I | `test_segmentation_model.py::test_train_needs_enough_varied_customers`, `::test_assign_new_customer_to_nearest_center`; `test_segmentation_db.py::test_assign_uses_current_model_without_retraining`, `::test_assign_without_model_trains_first_and_without_orders_removes_row` |
| TC-54 | BR-23 (#59) | Huấn luyện lại mỗi tuần; mỗi đêm gán lại theo R mới (khách lâu không mua chuyển nhóm); lỗi phân nhóm không chặn gợi ý | + / − | I | `test_segmentation_db.py::test_retrain_if_due_once_a_week`, `::test_nightly_reassigns_with_current_recency_without_retraining`, `::test_reassign_moves_lapsed_customer_to_lost`, `::test_nightly_job_survives_segmentation_error` |
| TC-55 | US-18, US-19 | Admin xem danh sách khách (lọc tên, email, nhóm), chi tiết, đơn; kết quả phân nhóm; khách bị cấm | + / − | I, F | `CustomerAdminIntegrationTest.*` (9 ca); `pages/SegmentPages.test.jsx`, `pages/AdminListPages.test.jsx` |
| TC-56 | US-25, BR-44 | Hồ sơ nhóm: doanh thu, nhịp mua, danh mục, giới tính và tuổi; danh sách cần chăm sóc theo nhịp riêng của khách | + / B | I | `SegmentProfileIntegrationTest.profileSummarizesRevenueRhythmAndCategories`, `.profileSplitsCustomersByGenderAndAge`, `.careListHasRepeatBuyersPastTheirOwnRhythm`, `.segmentWithoutRepeatBuyersHasNoRhythm` |
| TC-57 | US-21, BR-30–BR-33 | Mã hợp lệ được áp ở bước xem trước, mã sai có lý do; đặt đơn dùng mã 1 lần, hủy đơn trả lại lượt; hết lượt thì dừng; không giảm hàng xuống dưới 5.000đ | + / − / B | I, F | `VoucherIntegrationTest.previewAppliesValidCodeAndExplainsWhyOthersFail`, `.placingOrderWithVoucherUsesItOnceAndCancellingGivesItBack`, `.voucherStopsAtTotalUsageLimit`, `.discountNeverTakesGoodsBelowFiveThousand`; `pages/CheckoutPage.test.jsx` |
| TC-58 | US-22 | Admin tạo, sửa, tắt mã; dữ liệu sai bị từ chối; chỉ admin | + / − | I, F | `VoucherIntegrationTest.adminCreatesListsUpdatesAndTurnsOffVoucher`, `.invalidVouchersAreRejected`, `.onlyAdminManagesVouchers`; `pages/VoucherAdminPage.test.jsx` |
| TC-59 | US-24, BR-43 | Tặng mã cho khách cần chăm sóc, chỉ họ dùng được; đếm đơn dùng mã; cần người nhận và mã hợp lệ | + / − | I | `SegmentProfileIntegrationTest.giftingCareCustomersCreatesCodeOnlyTheyCanUse`, `.campaignResultsCountOrdersPlacedWithTheCode`, `.campaignNeedsRecipientsValidCodeAndAdmin` |
| TC-60 | US-24, BR-43 (#109) | Tự tặng mã chỉ cho khách vào nhóm sau khi bật; tối đa 1 mã mỗi 30 ngày khi đổi nhóm; 1 chương trình mỗi nhóm | + / B | I, U | `SegmentProfileIntegrationTest.autoOfferGoesOnlyToCustomersWhoEnterTheGroupAfterItStarts`, `.customerGetsAtMostOneAutoOfferIn30DaysWhenMovingBetweenGroups`, `.autoOfferNeedsDaysRunsOneAtATimeAndStops`; `CampaignJobsTest.eachRunGrantsAutoOffers` |

### 6.7 Quản trị (US-16, US-17, US-26) và tính năng phụ (US-14, US-15)

| Mã | US / BR | Tiêu chí chấp nhận (tóm tắt) | Loại | Tầng | Test tự động |
|---|---|---|---|---|---|
| TC-61 | US-16, BR-05 | Tạo sản phẩm với biến thể và ảnh; không phân loại thì đúng 1 biến thể; số giá trị phải bằng số tầng; trùng tổ hợp hoặc SKU bị từ chối | + / − | I, U, F | `CatalogAdminIntegrationTest.createProductWithVariantsAndImages`, `.productWithoutVariationHasExactlyOneVariant`, `.variantValuesMustMatchVariationCount`, `.duplicateVariantOrSkuIsRejected`; `ProductVariantTest.*`; `pages/CatalogAdminPages.test.jsx` |
| TC-62 | US-16, BR-36 | Xóa mềm ẩn sản phẩm và trả SKU về NULL; danh mục còn sản phẩm không xóa được; trùng tên không phân biệt hoa thường | − | I | `CatalogAdminIntegrationTest.softDeleteHidesProductAndReleasesSku`, `.categoryWithProductsCannotBeDeleted`, `.categoryCrudAndDuplicateNameIgnoringCase` |
| TC-63 | US-16 | API admin từ chối khách và người chưa đăng nhập | − | I | `CatalogAdminIntegrationTest.adminApisRejectCustomerAndAnonymous` |
| TC-64 | US-17, BR-12, BR-13 | Admin chỉ chuyển đơn tới trạng thái kế tiếp; hủy được đến trước Đã giao | + / − | I, E | `OrderIntegrationTest.adminMovesOrderForwardOneStepAndCanCancelUntilDelivered`; `e2e/admin-order.spec.js`; `pages/OrderAdminDetailPage.test.jsx` |
| TC-65 | US-26, BR-45 | Bảng điều khiển: doanh thu đơn Đã giao theo ngày giờ Việt Nam, bán chạy, hiệu quả gợi ý; khoảng trống ra số 0; khoảng ngày sai trả 400 | + / − / B | I, F | `DashboardIntegrationTest.*` (3 ca); `pages/DashboardPage.test.jsx` |
| TC-66 | US-14 | Thêm 2 lần vẫn 1 dòng, mới nhất trước; bỏ không có cũng không lỗi; tối đa 100; chỉ khách | + / − / B | I, F | `WishlistIntegrationTest.*` (4 ca); `pages/WishlistPage.test.jsx`, `components/WishlistButton.test.jsx` |
| TC-67 | US-15, BR-17, BR-18 | Chỉ sản phẩm thuộc đơn Đã giao mới đánh giá được, sửa được; dữ liệu sai và sản phẩm đã xóa bị từ chối; danh sách chờ đánh giá mỗi sản phẩm 1 lần | + / − | I, F | `ReviewIntegrationTest.*` (6 ca); `components/ProductReviews.test.jsx`, `components/PendingReviews.test.jsx` |

### 6.8 Dữ liệu mẫu và dịch vụ nền

| Mã | Yêu cầu | Nội dung | Loại | Tầng | Test tự động |
|---|---|---|---|---|---|
| TC-68 | Danh mục mẫu (BR-05, BR-36) | 1.050 sản phẩm của file mẫu qua đủ quy tắc như admin tự thêm; chữ ở dạng NFC; chỉ nạp khi bảng trống | + / − | U | `CatalogSeedServiceTest.*` (5 ca); `test_build_tiki_catalog.py` (16 ca, gồm tầng Màu sắc) |
| TC-69 | Dữ liệu mô phỏng | Chỉ đụng tài khoản `@mophong.test`; kiểu khách tái tạo được; bản đồ dịp sử dụng cố định (#126) | + | U | `test_simulation.py` (21 ca) |
| TC-70 | Sức khỏe dịch vụ | `GET /health` của ml-service trả UP mà không chạm CSDL; cấu hình đọc từ biến môi trường | + | U | `test_health.py`, `test_config.py` |

## 7. Ca kiểm thử thủ công

Các ca này cần dịch vụ bên ngoài, tài khoản thật hoặc mắt người. Chúng được chạy tay và ghi kết quả.

| Mã | Nội dung | Ngày, nơi chạy | Kết quả |
|---|---|---|---|
| MT-01 | Thanh toán VNPay sandbox bằng thẻ test trên staging: VNPay gọi IPN về, đơn chuyển "Đã thanh toán" | 22/10/2026, staging | Pass (log `Nhận IPN VNPay`, PR #23) |
| MT-02 | Quên mật khẩu: nhận email thật qua Gmail SMTP, mở liên kết, đặt lại mật khẩu | 01/11/2026, máy sinh viên và staging | Pass |
| MT-03 | Khảo sát: chạy hết 7.377 đường trả lời qua `POST /api/survey/evaluate`, cả khi có và không có ml-service | 04/11/2026, môi trường thử | Pass. Kết quả: 0 sản phẩm sai giới tính, 0 lỗi, luôn đủ 8 sản phẩm, không ra ngoài loại đồ đã chọn |
| MT-04 | Sao lưu CSDL staging: dump rồi khôi phục vào CSDL mới, so số bảng và số dòng | 04/11/2026 | Pass (PR #114) |
| MT-05 | Giám sát: UptimeRobot báo email khi `/actuator/health` không trả `"status":"UP"` | 29/10/2026, staging | Pass (PR #61) |
| MT-06 | Dữ liệu mẫu có tầng Màu sắc sau khi nạp lại: 1.050 sản phẩm, 69 sản phẩm có Màu sắc; tài khoản demo đăng nhập được | 05/11/2026, máy sinh viên và staging | Pass |
| MT-07 | Đánh giá ngay ở chi tiết đơn Đã giao, mục "Chờ đánh giá", dải nhắc ở trang chủ | 05/11/2026, máy thử (backend, MySQL, bản build frontend) | Pass (PR #134) |
| MT-08 | Ảnh minh họa: 42/42 danh mục có ảnh, không có ảnh trùng giữa các danh mục, sản phẩm đứng cạnh nhau khác ảnh | 05/11/2026, xem tờ ảnh thu nhỏ | Pass (PR #131, #135) |

## 8. Kết quả kiểm thử

### 8.1 Test tự động trên `main` (commit `c415b14`, 06/11/2026)

| Bộ test | Số test | Kết quả | Độ phủ dòng |
|---|---:|---|---|
| Backend (JUnit, JaCoCo) | 220 lần chạy | 220 pass, 0 lỗi | 93,4% (nhánh 84,0%) |
| ml-service (pytest, pytest-cov) | 159 | 159 pass | 97,3% (nhánh 90,6%) |
| Frontend (Vitest) | 409 (53 file) | 409 pass | 86,7% (nhánh 82,4%) |
| E2E (Playwright, Chromium) | 6 | 6 pass | — |

- **CI:** các job Backend, Frontend, ML service, Docker images, SonarQube Cloud, gitleaks và Deploy staging đều xanh.
- **Từ 22/10 đến 06/11/2026:** 50 lần deploy staging thành công.

### 8.2 Chỉ tiêu phi chức năng

Đo ngày 04/11/2026: 50 khách đồng thời, 180 giây, 12.933 yêu cầu. Chi tiết môi trường đo ở `perf/README.md`.

| Chỉ tiêu (Bảng 2.9) | Ngưỡng | Kết quả | Đạt |
|---|---|---|---|
| P95 đọc dữ liệu (danh sách, tìm kiếm, chi tiết, gợi ý trang chủ) | < 1.000 ms | 284 ms | Đạt |
| P95 gợi ý tính khi yêu cầu (liên quan, mua cùng) | < 1.500 ms | 138 ms | Đạt |
| P95 ghi có giao dịch (đăng ký, địa chỉ, lượt xem, giỏ) | < 2.000 ms | 91 ms | Đạt |
| P95 xác nhận đơn hàng | < 2.000 ms | 119 ms | Đạt |
| Gợi ý đổi sau hành động rõ ý | ≤ 10 s | khoảng 7,4 s (P95) | Đạt |
| Gợi ý đổi sau lượt xem | ≤ 1 phút | khoảng 34 s (P95) | Đạt |
| Tính lại gợi ý cho 100.000 khách mỗi đêm | < 30 phút | khoảng 29 phút (đo 17,5 ms mỗi khách) | Đạt, sát ngưỡng |
| Lighthouse Accessibility (KPI K8), 6 trang | ≥ 90 | 100 cả 6 trang | Đạt |

### 8.3 Chất lượng mô hình học máy

Đây là đánh giá ngoại tuyến trên **dữ liệu mô phỏng**, chưa phải dữ liệu người dùng thật (Mục 9).

**Gợi ý** (seed 2026, 209 khách có hành vi; đo ở 10 gợi ý đầu):

| Cách gợi ý | Tỷ lệ trúng | NDCG |
|---|---:|---:|
| Kết hợp (hybrid) | 0,287 | 0,106 |
| Chỉ bán chạy | 0,124 | 0,052 |
| Khách mới, khảo sát + bán chạy (172 khách) | 0,378 | — |
| Khách mới, chỉ bán chạy | 0,233 | — |

- Kết quả trên 3 seed: hybrid trúng 0,209–0,287, gấp 2,1–2,3 lần chỉ bán chạy.
- Số liệu đầy đủ ở Bảng 4.1 và 4.4 của báo cáo.

**Phân nhóm:** K = 4, Silhouette 0,384 (KPI ≥ 0,3), ARI 0,50 so với kiểu khách thật của dữ liệu mô phỏng.

### 8.4 Lỗi

Có 57 issue lỗi, đã đóng 57, không còn lỗi đang mở. Lỗi Critical duy nhất (#94) đã sửa và có test hồi quy (TC-16).

## 9. Hạn chế và việc còn lại

- **Chưa thực nghiệm với người dùng thật:** chưa đo SUS, A/B chưa có số liệu thật.
  - Thử nghiệm A/B đã làm xong và có test (TC-51).
  - Muốn có kết luận cần khoảng 900 khách mỗi nhóm.
- **Số liệu học máy ở Mục 8.3 là dữ liệu mô phỏng:** chỉ cho biết mô hình chạy đúng hướng.
- **E2E chỉ chạy Chromium bản desktop với 6 ca.** Các luồng VNPay, khảo sát, mã giảm giá, quản trị sản phẩm chỉ có test integration và Vitest, chưa có E2E.
- **Postman collection chưa chạy tự động** (chưa dùng newman). Tầng API được phủ bằng MockMvc trong các lớp `*IntegrationTest`.
- **Đo tải chạy trên máy thử đã giới hạn 2 vCPU** cho giống staging. Đo tải gọi thẳng backend, chưa đo staging thật qua internet. Lệnh đo chế độ chỉ đọc trên staging có ở `perf/README.md`.
- **Cổng chất lượng SonarQube mới để báo cáo** (gói Free), chưa chặn merge. Bù lại, quy trình bắt buộc đọc kết quả SonarQube trước khi merge (`AI_REVIEW.MD`, R5).
