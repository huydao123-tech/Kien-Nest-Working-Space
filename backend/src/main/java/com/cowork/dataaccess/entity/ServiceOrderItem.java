package com.cowork.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Món trong đơn gọi đồ ăn uống. Bảng: {@code service_order_item}. */
@Entity
@Table(name = "service_order_item")
public class ServiceOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private ServiceOrder serviceOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "is_free_drink", nullable = false)
    private boolean isFreeDrink = false;

    public ServiceOrderItem() {
    }

    public ServiceOrderItem(ServiceOrder serviceOrder, MenuItem menuItem, int quantity, long unitPrice, boolean isFreeDrink) {
        this.serviceOrder = serviceOrder;
        this.menuItem = menuItem;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.isFreeDrink = isFreeDrink;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ServiceOrder getServiceOrder() {
        return serviceOrder;
    }

    public void setServiceOrder(ServiceOrder serviceOrder) {
        this.serviceOrder = serviceOrder;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.menuItem = menuItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(long unitPrice) {
        this.unitPrice = unitPrice;
    }

    public boolean isFreeDrink() {
        return isFreeDrink;
    }

    public void setFreeDrink(boolean freeDrink) {
        isFreeDrink = freeDrink;
    }
}

