package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.MenuCategory;
import com.cowork.dataaccess.entity.MenuItem;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.repository.MenuCategoryRepository;
import com.cowork.dataaccess.repository.MenuItemRepository;
import com.cowork.dto.CommerceDtos.MenuCategoryDetail;
import com.cowork.dto.CommerceDtos.MenuCategoryRequest;
import com.cowork.dto.CommerceDtos.MenuItemDetail;
import com.cowork.dto.CommerceDtos.MenuItemRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** FR-ADM-07: quản trị thực đơn đồ ăn / uống (nhóm món và món). Gọi món (UC10) chưa nằm trong phạm vi. */
@Service
@Transactional
public class MenuService {

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final BranchRepository branches;

    public MenuService(MenuCategoryRepository categories, MenuItemRepository items, BranchRepository branches) {
        this.categories = categories;
        this.items = items;
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public List<MenuCategoryDetail> listCategories() {
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(MenuCategoryDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public MenuCategoryDetail getCategory(Long id) {
        return MenuCategoryDetail.from(category(id));
    }

    public MenuCategoryDetail createCategory(MenuCategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ConflictException("NAME_ALREADY_EXISTS", "Nhóm món đã tồn tại: " + name);
        }
        return MenuCategoryDetail.from(categories.save(new MenuCategory(name, request.sortOrder())));
    }

    public MenuCategoryDetail updateCategory(Long id, MenuCategoryRequest request) {
        MenuCategory category = category(id);
        String name = request.name().trim();
        if (!name.equalsIgnoreCase(category.getName()) && categories.existsByNameIgnoreCase(name)) {
            throw new ConflictException("NAME_ALREADY_EXISTS", "Nhóm món đã tồn tại: " + name);
        }
        category.setName(name);
        category.setSortOrder(request.sortOrder());
        return MenuCategoryDetail.from(categories.save(category));
    }

    /** Nhóm còn món thì không xóa được (409 DATA_CONFLICT). */
    public void deleteCategory(Long id) {
        categories.delete(category(id));
        categories.flush();
    }

    @Transactional(readOnly = true)
    public List<MenuItemDetail> listItems() {
        return items.findAllByOrderByIdAsc().stream().map(MenuItemDetail::from).toList();
    }

    @Transactional(readOnly = true)
    public MenuItemDetail getItem(Long id) {
        return MenuItemDetail.from(item(id));
    }

    public MenuItemDetail createItem(MenuItemRequest request) {
        MenuItem item = new MenuItem();
        applyItem(item, request);
        return MenuItemDetail.from(items.save(item));
    }

    public MenuItemDetail updateItem(Long id, MenuItemRequest request) {
        MenuItem item = item(id);
        applyItem(item, request);
        return MenuItemDetail.from(items.save(item));
    }

    /** Món đã có người gọi thì không xóa được (409 DATA_CONFLICT) — đặt available = false để ẩn. */
    public void deleteItem(Long id) {
        items.delete(item(id));
        items.flush();
    }

    private void applyItem(MenuItem item, MenuItemRequest request) {
        item.setBranch(branches.findById(request.branchId())
                .orElseThrow(() -> new NotFoundException("Chi nhánh", request.branchId())));
        item.setCategory(category(request.categoryId()));
        item.setName(request.name().trim());
        item.setPrice(request.price());
        item.setAvailable(request.available());
        item.setDrink(request.drink());
    }

    private MenuCategory category(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Nhóm món", id));
    }

    private MenuItem item(Long id) {
        return items.findById(id).orElseThrow(() -> new NotFoundException("Món", id));
    }
}
