package com.cowork.presentation.controller;

import com.cowork.business.service.AddonService;
import com.cowork.business.service.MembershipService;
import com.cowork.business.service.MenuService;
import com.cowork.business.service.VoucherService;
import com.cowork.dto.CommerceDtos.AddonDetail;
import com.cowork.dto.CommerceDtos.AddonRequest;
import com.cowork.dto.CommerceDtos.MembershipPlanDetail;
import com.cowork.dto.CommerceDtos.MembershipPlanRequest;
import com.cowork.dto.CommerceDtos.MenuCategoryDetail;
import com.cowork.dto.CommerceDtos.MenuCategoryRequest;
import com.cowork.dto.CommerceDtos.MenuItemDetail;
import com.cowork.dto.CommerceDtos.MenuItemRequest;
import com.cowork.dto.CommerceDtos.VoucherDetail;
import com.cowork.dto.CommerceDtos.VoucherRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** UC19, UC21: CRUD add-on, thực đơn, voucher, gói thành viên; và danh sách add-on công khai cho khách chọn khi đặt chỗ. */
@RestController
@RequestMapping("/api")
class CommerceController {

    private final AddonService addons;
    private final MenuService menu;
    private final VoucherService vouchers;
    private final MembershipService memberships;

    CommerceController(AddonService addons, MenuService menu, VoucherService vouchers, MembershipService memberships) {
        this.addons = addons;
        this.menu = menu;
        this.vouchers = vouchers;
        this.memberships = memberships;
    }

    /** FR-BOOK-02: add-on đang cho thuê của chi nhánh (công khai, không cần đăng nhập). */
    @GetMapping("/addons")
    List<AddonDetail> activeAddons(@RequestParam Long branchId) {
        return addons.listActive(branchId);
    }

    // ---------------- addon ----------------

    @GetMapping("/admin/addons")
    @PreAuthorize(Roles.ALL_STAFF)
    List<AddonDetail> listAddons() {
        return addons.list();
    }

    @GetMapping("/admin/addons/{id}")
    @PreAuthorize(Roles.ALL_STAFF)
    AddonDetail getAddon(@PathVariable Long id) {
        return addons.get(id);
    }

    @PostMapping("/admin/addons")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    AddonDetail createAddon(@Valid @RequestBody AddonRequest request) {
        return addons.create(request);
    }

    @PutMapping("/admin/addons/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    AddonDetail updateAddon(@PathVariable Long id, @Valid @RequestBody AddonRequest request) {
        return addons.update(id, request);
    }

    @DeleteMapping("/admin/addons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deleteAddon(@PathVariable Long id) {
        addons.delete(id);
    }

    // ---------------- menu_category ----------------

    @GetMapping("/admin/menu-categories")
    @PreAuthorize(Roles.FRONT_DESK)
    List<MenuCategoryDetail> listCategories() {
        return menu.listCategories();
    }

    @GetMapping("/admin/menu-categories/{id}")
    @PreAuthorize(Roles.FRONT_DESK)
    MenuCategoryDetail getCategory(@PathVariable Long id) {
        return menu.getCategory(id);
    }

    @PostMapping("/admin/menu-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    MenuCategoryDetail createCategory(@Valid @RequestBody MenuCategoryRequest request) {
        return menu.createCategory(request);
    }

    @PutMapping("/admin/menu-categories/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    MenuCategoryDetail updateCategory(@PathVariable Long id, @Valid @RequestBody MenuCategoryRequest request) {
        return menu.updateCategory(id, request);
    }

    @DeleteMapping("/admin/menu-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deleteCategory(@PathVariable Long id) {
        menu.deleteCategory(id);
    }

    // ---------------- menu_item ----------------

    @GetMapping("/admin/menu-items")
    @PreAuthorize(Roles.FRONT_DESK)
    List<MenuItemDetail> listItems() {
        return menu.listItems();
    }

    @GetMapping("/admin/menu-items/{id}")
    @PreAuthorize(Roles.FRONT_DESK)
    MenuItemDetail getItem(@PathVariable Long id) {
        return menu.getItem(id);
    }

    @PostMapping("/admin/menu-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    MenuItemDetail createItem(@Valid @RequestBody MenuItemRequest request) {
        return menu.createItem(request);
    }

    @PutMapping("/admin/menu-items/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    MenuItemDetail updateItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        return menu.updateItem(id, request);
    }

    @DeleteMapping("/admin/menu-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deleteItem(@PathVariable Long id) {
        menu.deleteItem(id);
    }

    // ---------------- voucher ----------------

    @GetMapping("/admin/vouchers")
    @PreAuthorize(Roles.FRONT_DESK)
    List<VoucherDetail> listVouchers() {
        return vouchers.list();
    }

    @GetMapping("/admin/vouchers/{id}")
    @PreAuthorize(Roles.FRONT_DESK)
    VoucherDetail getVoucher(@PathVariable Long id) {
        return vouchers.get(id);
    }

    @PostMapping("/admin/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    VoucherDetail createVoucher(@Valid @RequestBody VoucherRequest request) {
        return vouchers.create(request);
    }

    @PutMapping("/admin/vouchers/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    VoucherDetail updateVoucher(@PathVariable Long id, @Valid @RequestBody VoucherRequest request) {
        return vouchers.update(id, request);
    }

    @DeleteMapping("/admin/vouchers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deleteVoucher(@PathVariable Long id) {
        vouchers.delete(id);
    }

    // ---------------- membership_plan ----------------

    @GetMapping("/admin/membership-plans")
    @PreAuthorize(Roles.FRONT_DESK)
    List<MembershipPlanDetail> listPlans() {
        return memberships.listPlans();
    }

    @GetMapping("/admin/membership-plans/{id}")
    @PreAuthorize(Roles.FRONT_DESK)
    MembershipPlanDetail getPlan(@PathVariable Long id) {
        return memberships.getPlan(id);
    }

    @PostMapping("/admin/membership-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    MembershipPlanDetail createPlan(@Valid @RequestBody MembershipPlanRequest request) {
        return memberships.createPlan(request);
    }

    @PutMapping("/admin/membership-plans/{id}")
    @PreAuthorize(Roles.ADMIN_MANAGER)
    MembershipPlanDetail updatePlan(@PathVariable Long id, @Valid @RequestBody MembershipPlanRequest request) {
        return memberships.updatePlan(id, request);
    }

    @DeleteMapping("/admin/membership-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.ADMIN_MANAGER)
    void deletePlan(@PathVariable Long id) {
        memberships.deletePlan(id);
    }
}
