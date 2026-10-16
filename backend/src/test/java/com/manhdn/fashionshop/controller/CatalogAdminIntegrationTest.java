package com.manhdn.fashionshop.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra API quản lý danh mục, thương hiệu, sản phẩm (US-16) qua HTTP với MySQL thật. Quyền được
 * giả lập bằng JWT có sẵn vai trò (SecurityMockMvcRequestPostProcessors.jwt), phần cấp token thật
 * đã kiểm tra ở AuthControllerIntegrationTest. Mỗi test rollback ở cuối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogAdminIntegrationTest {

  private static final RequestPostProcessor ADMIN =
      jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  private static final RequestPostProcessor CUSTOMER =
      jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

  @Autowired private MockMvc mockMvc;

  // ---------- Danh mục, thương hiệu ----------

  @Test
  void categoryCrudAndDuplicateNameIgnoringCase() throws Exception {
    long id = createCategory("Giày nam");
    send(post("/api/admin/categories"), "{\"name\":\"GIÀY NAM\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Tên danh mục đã tồn tại"));

    send(put("/api/admin/categories/" + id), "{\"name\":\"Giày thể thao nam\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Giày thể thao nam"));
    mockMvc
        .perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Giày thể thao nam')]").exists());

    mockMvc
        .perform(delete("/api/admin/categories/" + id).with(ADMIN))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(delete("/api/admin/categories/" + id).with(ADMIN))
        .andExpect(status().isNotFound());
  }

  @Test
  void categoryWithProductsCannotBeDeleted() throws Exception {
    long categoryId = createCategory("Túi xách");
    createProduct(categoryId, null, "Túi tote", variant("Đen", "Free size", 250000, 5, null));

    mockMvc
        .perform(delete("/api/admin/categories/" + categoryId).with(ADMIN))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Danh mục còn sản phẩm, không xóa được"));
  }

  @Test
  void brandCrud() throws Exception {
    long id =
        id(
            send(post("/api/admin/brands"), "{\"name\":\"Biti's\"}")
                .andExpect(status().isCreated()));
    send(post("/api/admin/brands"), "{\"name\":\"biti's\"}").andExpect(status().isConflict());
    mockMvc.perform(get("/api/brands")).andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/admin/brands/" + id).with(ADMIN))
        .andExpect(status().isNoContent());
  }

  @Test
  void adminApisRejectCustomerAndAnonymous() throws Exception {
    mockMvc.perform(get("/api/admin/products").with(CUSTOMER)).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/admin/categories")
                .with(CUSTOMER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
  }

  // ---------- Sản phẩm ----------

  @Test
  void createProductWithVariantsAndImages() throws Exception {
    long categoryId = createCategory("Giày nữ");
    long brandId =
        id(
            send(post("/api/admin/brands"), "{\"name\":\"Ananas\"}")
                .andExpect(status().isCreated()));

    send(
            post("/api/admin/products"),
            """
            {"name":" Giày búp bê ","description":"Da mềm","categoryId":%d,"brandId":%d,
             "variationName1":" Màu sắc ","variationName2":"Kích cỡ",
             "imageUrls":["https://img/1.jpg","https://img/2.jpg"],
             "variants":[%s,%s]}
            """
                .formatted(
                    categoryId,
                    brandId,
                    variant("Kem", "36", 350000, 10, "BB-36-KEM"),
                    variant(" Kem ", "37", 360000, 0, null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Giày búp bê"))
        .andExpect(jsonPath("$.variationName1").value("Màu sắc"))
        .andExpect(jsonPath("$.variationName2").value("Kích cỡ"))
        .andExpect(jsonPath("$.category.name").value("Giày nữ"))
        .andExpect(jsonPath("$.brand.name").value("Ananas"))
        .andExpect(jsonPath("$.imageUrls[1]").value("https://img/2.jpg"))
        .andExpect(jsonPath("$.variants.length()").value(2))
        .andExpect(jsonPath("$.variants[0].option1Value").value("Kem"))
        .andExpect(jsonPath("$.variants[0].option2Value").value("36"))
        .andExpect(jsonPath("$.variants[0].skuCode").value("BB-36-KEM"))
        .andExpect(jsonPath("$.variants[1].option1Value").value("Kem"))
        .andExpect(jsonPath("$.deletedAt").doesNotExist());
  }

  @Test
  void productWithoutVariationHasExactlyOneVariant() throws Exception {
    long categoryId = createCategory("Sách");
    String body =
        productJsonWithTiers(
            categoryId, null, "Sổ tay", null, null, variant(null, null, 45000, 20, null));
    long productId = id(send(post("/api/admin/products"), body).andExpect(status().isCreated()));
    mockMvc
        .perform(get("/api/admin/products/" + productId).with(ADMIN))
        .andExpect(jsonPath("$.variationName1").doesNotExist())
        .andExpect(jsonPath("$.variants.length()").value(1))
        .andExpect(jsonPath("$.variants[0].option1Value").doesNotExist());

    send(
            post("/api/admin/products"),
            productJsonWithTiers(
                categoryId,
                null,
                "Bút",
                null,
                null,
                variant(null, null, 5000, 1, null),
                variant(null, null, 6000, 1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("Sản phẩm không có phân loại chỉ có đúng 1 biến thể"));
    send(
            post("/api/admin/products"),
            productJsonWithTiers(
                categoryId, null, "Bút", null, null, variant("Xanh", null, 5000, 1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Sản phẩm không có phân loại, biến thể không có giá trị phân loại"));
    send(
            post("/api/admin/products/%d/variants".formatted(productId)),
            variant(null, null, 46000, 1, null))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value("Sản phẩm không có phân loại chỉ có đúng 1 biến thể"));
  }

  @Test
  void variantValuesMustMatchVariationCount() throws Exception {
    long categoryId = createCategory("Điện thoại");
    send(
            post("/api/admin/products"),
            productJsonWithTiers(
                categoryId,
                null,
                "Điện thoại A",
                "Dung lượng",
                null,
                variant(null, null, 1, 1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("Phải nhập giá trị cho tầng phân loại \"Dung lượng\""));
    send(
            post("/api/admin/products"),
            productJsonWithTiers(
                categoryId,
                null,
                "Điện thoại A",
                "Dung lượng",
                null,
                variant("64GB", "Đen", 1, 1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Sản phẩm chỉ có 1 tầng phân loại, không nhập giá trị cho tầng 2"));
    send(
            post("/api/admin/products"),
            productJson(categoryId, null, "Điện thoại A", variant("Đen", " ", 1, 1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Phải nhập giá trị cho tầng phân loại \"Kích cỡ\""));
    send(
            post("/api/admin/products"),
            productJsonWithTiers(
                categoryId, null, "Điện thoại A", null, "Màu", variant("Đen", null, 1, 1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Có tầng phân loại 2 thì phải có tầng phân loại 1"));
    send(
            post("/api/admin/products"),
            productJsonWithTiers(
                categoryId, null, "Điện thoại A", "Màu", "MÀU", variant("Đen", "Đỏ", 1, 1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Hai tầng phân loại phải có tên khác nhau"));

    long productId =
        id(
            send(
                    post("/api/admin/products"),
                    productJsonWithTiers(
                        categoryId,
                        null,
                        "Điện thoại A",
                        "Dung lượng",
                        null,
                        variant("64GB", null, 5000000, 3, null)))
                .andExpect(status().isCreated()));
    send(
            post("/api/admin/products/%d/variants".formatted(productId)),
            variant("128GB", null, 6000000, 2, null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.option1Value").value("128GB"))
        .andExpect(jsonPath("$.option2Value").doesNotExist());
    send(
            post("/api/admin/products/%d/variants".formatted(productId)),
            variant("128gb", null, 6000000, 2, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Sản phẩm đã có biến thể 128gb"));
  }

  @Test
  void productNeedsAtLeastOneValidVariant() throws Exception {
    long categoryId = createCategory("Balo");
    send(
            post("/api/admin/products"),
            "{\"name\":\"Balo\",\"categoryId\":%d,\"variants\":[]}".formatted(categoryId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.variants").value("Sản phẩm phải có ít nhất 1 biến thể"));
    send(
            post("/api/admin/products"),
            productJson(categoryId, null, "Balo", variant("Xám", "M", 0, -1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['variants[0].price']").exists())
        .andExpect(jsonPath("$.errors['variants[0].stock']").exists());
  }

  @Test
  void unknownCategoryGives404() throws Exception {
    send(
            post("/api/admin/products"),
            productJson(999999, null, "X", variant("Đỏ", "M", 1, 1, null)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Không tìm thấy danh mục"));
  }

  @Test
  void duplicateVariantOrSkuIsRejected() throws Exception {
    long categoryId = createCategory("Phụ kiện");
    send(
            post("/api/admin/products"),
            productJson(
                categoryId,
                null,
                "Ví",
                variant("Nâu", "Free size", 100000, 1, null),
                variant("nau", "FREE SIZE", 120000, 1, null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Trùng biến thể nau – FREE SIZE"));

    createProduct(categoryId, null, "Ví A", variant("Đen", "Free size", 100000, 1, "VI-001"));
    send(
            post("/api/admin/products"),
            productJson(categoryId, null, "Ví B", variant("Đen", "Free size", 100000, 1, "VI-001")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Mã SKU VI-001 đã được dùng cho biến thể khác"));
  }

  @Test
  void softDeleteHidesProductAndReleasesSku() throws Exception {
    long categoryId = createCategory("Dép");
    long productId =
        createProduct(categoryId, null, "Dép lê", variant("Đen", "40", 90000, 3, "DEP-40-DEN"));

    mockMvc
        .perform(delete("/api/admin/products/" + productId).with(ADMIN))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/admin/products/" + productId).with(ADMIN))
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.variants[0].skuCode").doesNotExist());
    mockMvc
        .perform(get("/api/admin/products").param("keyword", "dép lê").with(ADMIN))
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(
            get("/api/admin/products")
                .param("keyword", "dép lê")
                .param("includeDeleted", "true")
                .with(ADMIN))
        .andExpect(jsonPath("$.content[0].deleted").value(true));

    // BR-36: mã SKU cũ dùng lại được cho sản phẩm mới
    createProduct(categoryId, null, "Dép lê mới", variant("Đen", "40", 95000, 3, "DEP-40-DEN"));
    // Sản phẩm đã ngừng bán không sửa được
    send(
            put("/api/admin/products/" + productId),
            "{\"name\":\"Đổi tên\",\"categoryId\":%d}".formatted(categoryId))
        .andExpect(status().isConflict());
  }

  @Test
  void updateProductReplacesInfoImagesAndRenamesVariations() throws Exception {
    long categoryId = createCategory("Vali");
    long otherCategory = createCategory("Balo du lịch");
    long productId =
        createProduct(categoryId, null, "Vali 20 inch", variant("Xanh", "20", 900000, 2, null));

    send(
            put("/api/admin/products/" + productId),
            """
            {"name":"Vali kéo 20 inch","description":"Khung nhôm","categoryId":%d,
             "variationName1":"Màu","variationName2":"Cỡ",
             "imageUrls":["https://img/v.jpg"]}
            """
                .formatted(otherCategory))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Vali kéo 20 inch"))
        .andExpect(jsonPath("$.variationName1").value("Màu"))
        .andExpect(jsonPath("$.variationName2").value("Cỡ"))
        .andExpect(jsonPath("$.category.name").value("Balo du lịch"))
        .andExpect(jsonPath("$.imageUrls.length()").value(1))
        .andExpect(jsonPath("$.variants.length()").value(1))
        .andExpect(jsonPath("$.variants[0].option1Value").value("Xanh"));

    // Bỏ tầng 2 thì biến thể đang có giá trị tầng 2 sẽ sai, nên không cho đổi số tầng
    send(
            put("/api/admin/products/" + productId),
            "{\"name\":\"Vali\",\"categoryId\":%d,\"variationName1\":\"Màu\"}"
                .formatted(categoryId))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Không đổi được số tầng phân loại của sản phẩm đã tạo (đang có 2 tầng). "
                        + "Muốn phân loại khác thì tạo sản phẩm mới"));
  }

  @Test
  void variantLifecycle() throws Exception {
    long categoryId = createCategory("Sandal");
    long productId =
        createProduct(categoryId, null, "Sandal", variant("Trắng", "38", 200000, 4, null));

    long variantId =
        id(
            send(
                    post("/api/admin/products/%d/variants".formatted(productId)),
                    variant("Trắng", "39", 210000, 6, "SD-39"))
                .andExpect(status().isCreated()));
    // Trùng tổ hợp không phân biệt hoa thường và dấu, giống collation của MySQL
    send(
            post("/api/admin/products/%d/variants".formatted(productId)),
            variant("trang", "39", 210000, 6, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Sản phẩm đã có biến thể trang – 39"));

    send(
            put("/api/admin/products/%d/variants/%d".formatted(productId, variantId)),
            variant("Trắng", "39", 199000, 0, "SD-39"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(199000))
        .andExpect(jsonPath("$.stock").value(0));
    send(
            put("/api/admin/products/%d/variants/%d".formatted(productId, variantId)),
            variant("Trắng", "38", 199000, 0, "SD-39"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Sản phẩm đã có biến thể Trắng – 38"));

    mockMvc
        .perform(
            delete("/api/admin/products/%d/variants/%d".formatted(productId, variantId))
                .with(ADMIN))
        .andExpect(status().isNoContent());
    long lastVariant =
        ((Number)
                JsonPath.read(
                    mockMvc
                        .perform(get("/api/admin/products/" + productId).with(ADMIN))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                    "$.variants[0].id"))
            .longValue();
    mockMvc
        .perform(
            delete("/api/admin/products/%d/variants/%d".formatted(productId, lastVariant))
                .with(ADMIN))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Sản phẩm phải còn ít nhất 1 biến thể"));
  }

  @Test
  void searchFiltersByKeywordAndCategoryWithPaging() throws Exception {
    long shoes = createCategory("Giày chạy bộ");
    long bags = createCategory("Túi đeo chéo");
    createProduct(shoes, null, "Giày chạy bộ Alpha", variant("Đỏ", "41", 500000, 1, null));
    createProduct(shoes, null, "Giày chạy bộ Beta", variant("Xanh", "42", 600000, 0, null));
    createProduct(bags, null, "Túi Alpha", variant("Đen", "Free size", 300000, 2, null));

    mockMvc
        .perform(
            get("/api/admin/products")
                .param("keyword", "alpha")
                .param("categoryId", String.valueOf(shoes))
                .with(ADMIN))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Giày chạy bộ Alpha"))
        .andExpect(jsonPath("$.content[0].minPrice").value(500000))
        .andExpect(jsonPath("$.content[0].totalStock").value(1));
    mockMvc
        .perform(
            get("/api/admin/products")
                .param("categoryId", String.valueOf(shoes))
                .param("size", "1")
                .with(ADMIN))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  // ---------- Hàm hỗ trợ ----------

  private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
    return mockMvc.perform(
        request.with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private long createCategory(String name) throws Exception {
    return id(
        send(post("/api/admin/categories"), "{\"name\":\"%s\"}".formatted(name))
            .andExpect(status().isCreated()));
  }

  /** Tạo sản phẩm có 2 tầng phân loại "Màu sắc", "Kích cỡ". */
  private long createProduct(long categoryId, Long brandId, String name, String variantJson)
      throws Exception {
    return id(
        send(post("/api/admin/products"), productJson(categoryId, brandId, name, variantJson))
            .andExpect(status().isCreated()));
  }

  /** JSON thêm sản phẩm có 2 tầng phân loại "Màu sắc", "Kích cỡ". */
  private static String productJson(
      long categoryId, Long brandId, String name, String... variantJsons) {
    return productJsonWithTiers(categoryId, brandId, name, "Màu sắc", "Kích cỡ", variantJsons);
  }

  /** JSON thêm sản phẩm với tên tầng phân loại cho trước (null = không dùng tầng đó). */
  private static String productJsonWithTiers(
      long categoryId,
      Long brandId,
      String name,
      String variationName1,
      String variationName2,
      String... variantJsons) {
    return """
        {"name":"%s","categoryId":%d,"brandId":%s,"variationName1":%s,"variationName2":%s,
         "variants":[%s]}
        """
        .formatted(
            name,
            categoryId,
            brandId,
            json(variationName1),
            json(variationName2),
            String.join(",", variantJsons));
  }

  private static String variant(String option1, String option2, long price, int stock, String sku) {
    return "{\"option1Value\":%s,\"option2Value\":%s,\"price\":%d,\"stock\":%d,\"skuCode\":%s}"
        .formatted(json(option1), json(option2), price, stock, json(sku));
  }

  private static String json(String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private static long id(ResultActions result) throws Exception {
    Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    return id.longValue();
  }
}
