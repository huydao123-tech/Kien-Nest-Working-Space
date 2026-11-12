package com.cowork.dataaccess.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Hóa đơn của đơn đặt chỗ. Bảng: {@code invoice}. */
@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "invoice_no", nullable = false, unique = true, length = 30)
    private String invoiceNo;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "e_invoice_lookup_code", length = 50)
    private String eInvoiceLookupCode;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    public Invoice() {
    }

    public Invoice(Booking booking, String invoiceNo, long totalAmount, String eInvoiceLookupCode) {
        this.booking = booking;
        this.invoiceNo = invoiceNo;
        this.totalAmount = totalAmount;
        this.eInvoiceLookupCode = eInvoiceLookupCode;
        this.issuedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getEInvoiceLookupCode() {
        return eInvoiceLookupCode;
    }

    public void setEInvoiceLookupCode(String eInvoiceLookupCode) {
        this.eInvoiceLookupCode = eInvoiceLookupCode;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public List<InvoiceLine> getLines() {
        return lines;
    }

    /** Thêm con và gán ngược về cha để hai phía quan hệ luôn khớp nhau. */
    public void addLine(InvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }
}
