package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.MenuCategory;
import com.cowork.dataaccess.entity.MenuItem;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.repository.MenuCategoryRepository;
import com.cowork.dataaccess.repository.MenuItemRepository;
import com.cowork.dto.CommerceDtos.MenuCategoryDetail;
import com.cowork.dto.CommerceDtos.MenuCategoryRequest;
import com.cowork.dto.CommerceDtos.MenuItemDetail;
import com.cowork.dto.CommerceDtos.MenuItemRequest;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FR-ADM-07: quản trị nhóm món và món. Viết theo {@code .claude/rules/unit-test.md}. */
public class MenuServiceTest {

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private MenuCategoryRepository categories;
    private MenuItemRepository items;
    private MenuService service;
    private Branch branch;
    private MenuCategory drinks;

    @Before
    public void setUp() {
        categories = mock(MenuCategoryRepository.class);
        items = mock(MenuItemRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        service = new MenuService(categories, items, branches);
        branch = new Branch("HCM-Q1", "Cowork", "72 Nguyễn Huệ", LocalTime.of(7, 0), LocalTime.of(22, 0));
        branch.setId(1L);
        drinks = new MenuCategory("Đồ uống", 1);
        drinks.setId(2L);
        when(branches.findById(1L)).thenReturn(Optional.of(branch));
        when(categories.findById(2L)).thenReturn(Optional.of(drinks));
        when(categories.save(any(MenuCategory.class))).thenAnswer(call -> call.getArgument(0));
        when(items.save(any(MenuItem.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- category ----------

    // Viewpoint: VP7
    @Test
    public void test_getCategory_Found_001() {
        // Có nhóm món.
        log.input("id = {}", 2L);

        MenuCategoryDetail detail = service.getCategory(2L);

        log.output("name = {}", detail.name());
        assertEquals("Đồ uống", detail.name());
    }

    // Viewpoint: VP7
    @Test
    public void test_getCategory_NotFound_002() {
        // Không có nhóm món.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.getCategory(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP2, VP3
    @Test
    public void test_createCategory_NewName_003() {
        // Tên mới (ký tự 3 byte), cắt khoảng trắng.
        MenuCategoryRequest request = new MenuCategoryRequest("  Bánh ngọt ệ ", 3);
        log.input("request = {}", request);

        MenuCategoryDetail detail = service.createCategory(request);

        log.output("name = {}, sortOrder = {}", detail.name(), detail.sortOrder());
        assertEquals("Bánh ngọt ệ", detail.name());
        assertEquals(3, detail.sortOrder());
    }

    // Viewpoint: VP3
    @Test
    public void test_createCategory_NameExists_004() {
        // Tên đã có (không phân biệt hoa thường).
        when(categories.existsByNameIgnoreCase("đồ uống")).thenReturn(true);
        MenuCategoryRequest request = new MenuCategoryRequest("đồ uống", 1);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.createCategory(request));

        log.output("code = {}", thrown.code());
        assertEquals("NAME_ALREADY_EXISTS", thrown.code());
        verify(categories, never()).save(any());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateCategory_SameNameOtherCase_005() {
        // Đổi hoa / thường của chính tên mình: không coi là trùng.
        when(categories.existsByNameIgnoreCase("ĐỒ UỐNG")).thenReturn(true);
        MenuCategoryRequest request = new MenuCategoryRequest("ĐỒ UỐNG", 5);
        log.input("id = {}, request = {}", 2L, request);

        MenuCategoryDetail detail = service.updateCategory(2L, request);

        log.output("name = {}, sortOrder = {}", detail.name(), detail.sortOrder());
        assertEquals("ĐỒ UỐNG", detail.name());
        assertEquals(5, detail.sortOrder());
    }

    // Viewpoint: VP3
    @Test
    public void test_updateCategory_NameOfOther_006() {
        // Đổi sang tên của nhóm khác.
        when(categories.existsByNameIgnoreCase("Đồ ăn")).thenReturn(true);
        MenuCategoryRequest request = new MenuCategoryRequest("Đồ ăn", 1);
        log.input("id = {}, request = {}", 2L, request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.updateCategory(2L, request));

        log.output("code = {}", thrown.code());
        assertEquals("NAME_ALREADY_EXISTS", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_deleteCategory_Found_007() {
        // Xóa và flush ngay.
        log.input("id = {}", 2L);

        service.deleteCategory(2L);

        log.output("deletedId = {}", drinks.getId());
        assertEquals(Long.valueOf(2L), drinks.getId());
        verify(categories).delete(drinks);
        verify(categories).flush();
    }

    // Viewpoint: VP7
    @Test
    public void test_deleteCategory_NotFound_008() {
        // Không có nhóm món.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.deleteCategory(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- item ----------

    // Viewpoint: VP7
    @Test
    public void test_createItem_Valid_009() {
        // Tạo món thuộc chi nhánh và nhóm.
        MenuItemRequest request = new MenuItemRequest(1L, 2L, "  Cà phê sữa ", 35_000, true, true);
        log.input("request = {}", request);

        MenuItemDetail detail = service.createItem(request);

        log.output("branchId = {}, categoryId = {}, name = {}, price = {}, available = {}, drink = {}", detail.branchId(),
                detail.categoryId(), detail.name(), detail.price(), detail.available(), detail.drink());
        assertEquals(Long.valueOf(1L), detail.branchId());
        assertEquals(Long.valueOf(2L), detail.categoryId());
        assertEquals("Cà phê sữa", detail.name());
        assertEquals(35_000L, detail.price());
        assertTrue(detail.available());
        assertTrue(detail.drink());
    }

    // Viewpoint: VP7
    @Test
    public void test_createItem_BranchNotFound_010() {
        // Chi nhánh không tồn tại.
        MenuItemRequest request = new MenuItemRequest(99L, 2L, "Cà phê", 35_000, true, true);
        log.input("request = {}", request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.createItem(request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
        verify(items, never()).save(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_createItem_CategoryNotFound_011() {
        // Nhóm món không tồn tại.
        MenuItemRequest request = new MenuItemRequest(1L, 99L, "Cà phê", 35_000, true, true);
        log.input("request = {}", request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.createItem(request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP1, VP5
    @Test
    public void test_updateItem_HideItem_012() {
        // Ẩn món (available = false), giá 0 (cận dưới).
        MenuItem item = new MenuItem(branch, drinks, "Trà", 20_000, true);
        item.setId(7L);
        when(items.findById(7L)).thenReturn(Optional.of(item));
        MenuItemRequest request = new MenuItemRequest(1L, 2L, "Trà", 0, false, true);
        log.input("id = {}, request = {}", 7L, request);

        MenuItemDetail detail = service.updateItem(7L, request);

        log.output("available = {}, price = {}", detail.available(), detail.price());
        assertEquals(false, detail.available());
        assertEquals(0L, detail.price());
    }

    // Viewpoint: VP7
    @Test
    public void test_getItem_NotFound_013() {
        // Không có món.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.getItem(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_deleteItem_Found_014() {
        // Xóa món và flush ngay.
        MenuItem item = new MenuItem(branch, drinks, "Trà", 20_000, true);
        item.setId(7L);
        when(items.findById(7L)).thenReturn(Optional.of(item));
        log.input("id = {}", 7L);

        service.deleteItem(7L);

        log.output("deletedId = {}", item.getId());
        assertEquals(Long.valueOf(7L), item.getId());
        verify(items).delete(item);
        verify(items).flush();
    }
}
