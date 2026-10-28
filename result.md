- thứ tự code là 
1. tạo các domain event cho context
2. Viết aggregation, hàm xử lí logic trong nó luôn
3. Nếu logic cần nhiều aggregation thì tạo riêng qua các domain service(cx trong domain)
4. Viết service và sử dụng các hàm logic đã viết để tạo 1 usecase hoàn chỉnh
5. Sau khi hàm thực thi thành công thì xử lí event sau nó
Ví dụ:
if (changed) {
            payments.save(payment);
            events.publishEventsOf(payment);
        }
gọi đến:
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(PaymentSucceeded event) {
        bookingService.onPaymentSucceeded(event.bookingId(), event.amount());
    }
qua đó cùng lúc thay đổi state của booking

6. Tạo api trong controller


đọc kĩ các diagram để hiểu cách các bound context tương tác với nhau
# Hướng dẫn vẽ Context Diagram
Đọc requirement gathering mục 2 và 4
# Hướng dẫn vẽ Swimlane Diagram

Swimlane thì **mỗi luồng chính một sơ đồ**. Dự án có 4 luồng chính, mỗi luồng chỉ cần đọc vài use case trong [use-cases.md](use-cases.md):

| # | Sơ đồ | Đọc use case | Đọc thêm tình huống ([requirement-gathering.md](../requirement-gathering.md)) |
| :---: | :--- | :--- | :--- |
| 1 | Đặt chỗ & thanh toán online | **UC03**, **UC04** | §5.1, §5.2 |
| 2 | Check-in → gia hạn → check-out | **UC07**, **UC08**, **UC09** | §5.3, §6.2 |
| 3 | Sự cố & đổi chỗ | **UC11**, **UC17**, **UC15** | §5.4, §6.1 |
| 4 | Đặt chỗ tại quầy (walk-in) | **UC13** | — |

## Một cách vẽ, dùng cho cả 4 sơ đồ

**① Chép luồng chính thành dạng "Ai — làm gì".** Mỗi bước trong mục *Luồng chính* của use case là một dòng.

**② Kẻ làn.** Mỗi "Ai" khác nhau xuất hiện ở bước ① là một làn.

**③ Đặt hộp.** Mỗi dòng ở bước ① thành một hộp, đặt trong làn của người làm. Nối mũi tên theo thứ tự bước.

**④ Thêm rẽ nhánh.** Đọc mục *Luồng thay thế* và *Ngoại lệ*. Mỗi cái thành một **hình thoi** ghi câu hỏi, với hai nhánh *Có / Không*.

**⑤ Thêm điểm đầu và điểm cuối.** Một điểm bắt đầu; mỗi nhánh đều phải đi tới một điểm kết thúc.