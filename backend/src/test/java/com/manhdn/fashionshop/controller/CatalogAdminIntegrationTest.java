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
    createProduct(categoryId, null, "Túi tote", variant("Free size", "Đen", 250000, 5, null));

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
             "imageUrls":["https://img/1.jpg","https://img/2.jpg"],
             "variants":[%s,%s]}
            """
                .formatted(
                    categoryId,
                    brandId,
                    variant("36", "Kem", 350000, 10, "BB-36-KEM"),
                    variant("37", "Kem", 360000, 0, null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Giày búp bê"))
        .andExpect(jsonPath("$.category.name").value("Giày nữ"))
        .andExpect(jsonPath("$.brand.name").value("Ananas"))
        .andExpect(jsonPath("$.imageUrls[1]").value("https://img/2.jpg"))
        .andExpect(jsonPath("$.variants.length()").value(2))
        .andExpect(jsonPath("$.variants[0].skuCode").value("BB-36-KEM"))
        .andExpect(jsonPath("$.deletedAt").doesNotExist());
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
            "{\"name\":\"Balo\",\"categoryId\":%d,\"variants\":[%s]}"
                .formatted(categoryId, variant("M", "Xám", 0, -1, null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['variants[0].price']").exists())
        .andExpect(jsonPath("$.errors['variants[0].stock']").exists());
  }

  @Test
  void unknownCategoryGives404() throws Exception {
    send(
            post("/api/admin/products"),
            "{\"name\":\"X\",\"categoryId\":999999,\"variants\":[%s]}"
                .formatted(variant("M", "Đỏ", 1000, 1, null)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Không tìm thấy danh mục"));
  }

  @Test
  void duplicateVariantOrSkuIsRejected() throws Exception {
    long categoryId = createCategory("Phụ kiện");
    send(
            post("/api/admin/products"),
            "{\"name\":\"Ví\",\"categoryId\":%d,\"variants\":[%s,%s]}"
                .formatted(
                    categoryId,
                    variant("Free size", "Nâu", 100000, 1, null),
                    variant("free size", "nâu", 120000, 1, null)))
        .andExpect(status().isConflict());

    createProduct(categoryId, null, "Ví A", variant("Free size", "Đen", 100000, 1, "VI-001"));
    send(
            post("/api/admin/products"),
            "{\"name\":\"Ví B\",\"categoryId\":%d,\"variants\":[%s]}"
                .formatted(categoryId, variant("Free size", "Đen", 100000, 1, "VI-001")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Mã SKU VI-001 đã được dùng cho biến thể khác"));
  }

  @Test
  void softDeleteHidesProductAndReleasesSku() throws Exception {
    long categoryId = createCategory("Dép");
    long productId =
        createProduct(categoryId, null, "Dép lê", variant("40", "Đen", 90000, 3, "DEP-40-DEN"));

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
    createProduct(categoryId, null, "Dép lê mới", variant("40", "Đen", 95000, 3, "DEP-40-DEN"));
    // Sản phẩm đã ngừng bán không sửa được
    send(
            put("/api/admin/products/" + productId),
            "{\"name\":\"Đổi tên\",\"categoryId\":%d}".formatted(categoryId))
        .andExpect(status().isConflict());
  }

  @Test
  void updateProductReplacesInfoAndImages() throws Exception {
    long categoryId = createCategory("Vali");
    long otherCategory = createCategory("Balo du lịch");
    long productId =
        createProduct(categoryId, null, "Vali 20 inch", variant("20", "Xanh", 900000, 2, null));

    send(
            put("/api/admin/products/" + productId),
            """
            {"name":"Vali kéo 20 inch","description":"Khung nhôm","categoryId":%d,
             "imageUrls":["https://img/v.jpg"]}
            """
                .formatted(otherCategory))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Vali kéo 20 inch"))
        .andExpect(jsonPath("$.category.name").value("Balo du lịch"))
        .andExpect(jsonPath("$.imageUrls.length()").value(1))
        .andExpect(jsonPath("$.variants.length()").value(1));
  }

  @Test
  void variantLifecycle() throws Exception {
    long categoryId = createCategory("Sandal");
    long productId =
        createProduct(categoryId, null, "Sandal", variant("38", "Trắng", 200000, 4, null));

    long variantId =
        id(
            send(
                    post("/api/admin/products/%d/variants".formatted(productId)),
                    variant("39", "Trắng", 210000, 6, "SD-39"))
                .andExpect(status().isCreated()));
    send(
            post("/api/admin/products/%d/variants".formatted(productId)),
            variant("39", "TRẮNG", 210000, 6, null))
        .andExpect(status().isConflict());

    send(
            put("/api/admin/products/%d/variants/%d".formatted(productId, variantId)),
            variant("39", "Trắng", 199000, 0, "SD-39"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(199000))
        .andExpect(jsonPath("$.stock").value(0));

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
    createProduct(shoes, null, "Giày chạy bộ Alpha", variant("41", "Đỏ", 500000, 1, null));
    createProduct(shoes, null, "Giày chạy bộ Beta", variant("42", "Xanh", 600000, 0, null));
    createProduct(bags, null, "Túi Alpha", variant("Free size", "Đen", 300000, 2, null));

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

  private long createProduct(long categoryId, Long brandId, String name, String variantJson)
      throws Exception {
    return id(
        send(
                post("/api/admin/products"),
                "{\"name\":\"%s\",\"categoryId\":%d,\"brandId\":%s,\"variants\":[%s]}"
                    .formatted(name, categoryId, brandId, variantJson))
            .andExpect(status().isCreated()));
  }

  private static String variant(String size, String color, long price, int stock, String sku) {
    return "{\"size\":\"%s\",\"color\":\"%s\",\"price\":%d,\"stock\":%d,\"skuCode\":%s}"
        .formatted(size, color, price, stock, sku == null ? "null" : "\"" + sku + "\"");
  }

  private static long id(ResultActions result) throws Exception {
    Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    return id.longValue();
  }
}
