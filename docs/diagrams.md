# Các Sơ Đồ Phân Tích Hệ Thống
**Dự án:** Hệ thống đặt chỗ Co-Working Space theo giờ
**Tài liệu liên quan:** [SRS](SRS.md) · [Đặc tả Use Case](use-cases.md) (gồm sơ đồ Use Case)

Mục lục:
1. [Context Diagram](#1-context-diagram)
2. [Swimlane Diagrams](#2-swimlane-diagrams)
3. [State Diagrams](#3-state-diagrams)

---

## 1. Context Diagram

Sơ đồ ngữ cảnh mức 0: toàn bộ hệ thống là một tiến trình duy nhất, xung quanh là các thực thể ngoài và luồng dữ liệu trao đổi.

```mermaid
flowchart LR
    KH["Khách hàng<br/>Member / Khách vãng lai"]
    LT["Lễ tân /<br/>Nhân viên vận hành"]
    QL["Quản lý trung tâm /<br/>Admin"]
    PG["Cổng thanh toán<br/>VietQR, ví điện tử, thẻ"]
    NS["Dịch vụ thông báo<br/>SMS, Zalo, Email, Push"]
    AC["Hệ thống kiểm soát<br/>ra vào & Wi-Fi"]
    EI["Nhà cung cấp<br/>hóa đơn điện tử"]

    SYS(("0<br/>HỆ THỐNG<br/>ĐẶT CHỖ<br/>CO-WORKING<br/>SPACE"))

    KH -->|"Tiêu chí tìm kiếm, yêu cầu đặt chỗ<br/>thông tin cá nhân, voucher, add-on<br/>yêu cầu gia hạn / hủy, gọi dịch vụ, báo sự cố"| SYS
    SYS -->|"Sơ đồ chỗ trống & báo giá<br/>Booking ID + QR, hóa đơn<br/>trạng thái đơn, trạng thái ticket"| KH

    LT -->|"Đơn walk-in, thao tác check-in/out<br/>lệnh đổi chỗ, phụ phí, thanh toán tại quầy<br/>cập nhật ticket, đánh dấu bảo trì, đền bù"| SYS
    SYS -->|"Bảng vận hành thời gian thực<br/>cảnh báo overstay, ticket mới<br/>đơn dịch vụ, biên lai"| LT

    QL -->|"Sơ đồ không gian, bảng giá<br/>tham số vận hành, voucher, gói membership<br/>tài khoản nhân viên, duyệt đền bù"| SYS
    SYS -->|"Báo cáo lấp đầy, doanh thu<br/>chỉ số vận hành, audit log"| QL

    SYS -->|"Yêu cầu giao dịch<br/>yêu cầu hoàn tiền, truy vấn trạng thái"| PG
    PG -->|"Webhook kết quả<br/>kết quả hoàn tiền, dữ liệu đối soát"| SYS

    SYS -->|"Nội dung thông báo<br/>OTP, QR, nhắc lịch, cảnh báo hết giờ"| NS
    NS -->|"Trạng thái gửi"| SYS

    SYS -->|"Lệnh mở cửa<br/>cấp / thu hồi tài khoản Wi-Fi"| AC
    AC -->|"Mã QR quét tại cửa<br/>sự kiện vào / ra"| SYS

    SYS -->|"Dữ liệu hóa đơn"| EI
    EI -->|"Số hóa đơn, mã tra cứu"| SYS

    classDef core fill:#1d39c4,stroke:#10239e,color:#fff,font-weight:bold
    classDef human fill:#fff7e6,stroke:#d48806,color:#000
    classDef extsys fill:#f0f5ff,stroke:#2f54eb,color:#000
    class SYS core
    class KH,LT,QL human
    class PG,NS,AC,EI extsys
```

| Thực thể ngoài | Dữ liệu vào hệ thống | Dữ liệu ra từ hệ thống |
| :--- | :--- | :--- |
| Khách hàng | Tiêu chí tìm, yêu cầu đặt/gia hạn/hủy, thông tin cá nhân, voucher, add-on, đơn dịch vụ, báo sự cố | Sơ đồ & giá, Booking ID + QR, hóa đơn, trạng thái đơn/ticket |
| Lễ tân | Đơn walk-in, check-in/out, đổi chỗ, phụ phí, thanh toán tại quầy, ticket, bảo trì, đền bù | Bảng vận hành, cảnh báo, đơn dịch vụ, biên lai |
| Quản lý | Cấu hình sơ đồ, bảng giá, tham số, khuyến mãi, nhân viên, phê duyệt | Báo cáo, audit log |
| Cổng thanh toán | Webhook, kết quả hoàn tiền, dữ liệu đối soát | Yêu cầu giao dịch, hoàn tiền, truy vấn |
| Dịch vụ thông báo | Trạng thái gửi | OTP, QR, nhắc lịch, cảnh báo |
| Kiểm soát ra vào / Wi-Fi | QR quét, sự kiện vào/ra | Lệnh mở cửa, cấp/thu hồi Wi-Fi |
| Hóa đơn điện tử | Số hóa đơn, mã tra cứu | Dữ liệu hóa đơn |

---

## 2. Swimlane Diagrams

Mỗi làn (lane) là một khung tương ứng với một tác nhân; hình thoi là điểm quyết định.

### 2.1. Đặt chỗ & thanh toán trực tuyến (gồm xử lý race condition và lỗi cổng thanh toán)

```mermaid
flowchart TB
    subgraph KH["👤 KHÁCH HÀNG"]
        K1([Bắt đầu])
        K2["Nhập tiêu chí: loại chỗ,<br/>ngày, giờ, số người"]
        K3["Chọn vị trí trên sơ đồ,<br/>add-on, voucher"]
        K4["Xác nhận đơn &<br/>chọn phương thức thanh toán"]
        K5["Thanh toán trên cổng"]
        K6([Nhận Booking ID + QR])
        K7([Nhận thông báo<br/>thất bại / hoàn tiền])
    end

    subgraph HT["🖥 HỆ THỐNG ĐẶT CHỖ"]
        S1["Truy vấn chỗ trống<br/>theo khung giờ"]
        S2{"Có chỗ<br/>phù hợp?"}
        S3["Gợi ý khung giờ /<br/>vị trí tương đương"]
        S4["Tính giá theo block giờ,<br/>áp voucher / membership"]
        S5{"Vị trí vẫn trống?<br/>kiểm tra nguyên tử"}
        S6["Soft lock 10 phút<br/>tạo đơn HELD"]
        S7{"Kết quả về trước<br/>khi hết giữ chỗ?"}
        S8["Đơn CONFIRMED: khóa chỗ,<br/>sinh QR, xuất hóa đơn,<br/>lên lịch nhắc"]
        S9["Đơn FAILED / EXPIRED:<br/>giải phóng chỗ & voucher"]
        S10["Đối soát giao dịch<br/>webhook trễ"]
        S11{"Vị trí còn trống?"}
        S12["Đơn CHỜ HOÀN TIỀN /<br/>đề xuất chỗ tương đương"]
    end

    subgraph PG["🏦 CỔNG THANH TOÁN"]
        P1["Xử lý giao dịch"]
        P2{"Thành công?"}
        P3["Gửi webhook"]
        P4["Thực hiện hoàn tiền"]
    end

    subgraph NS["📨 DỊCH VỤ THÔNG BÁO"]
        N1["Gửi Booking ID + QR"]
        N2["Gửi thông báo<br/>thất bại / hoàn tiền"]
    end

    K1 --> K2 --> S1 --> S2
    S2 -- Không --> S3 --> K2
    S2 -- Có --> K3 --> S4 --> K4 --> S5
    S5 -- "Không - bị người khác giữ" --> S3
    S5 -- Có --> S6 --> K5 --> P1 --> P2
    P2 -- Không --> S9
    P2 -- Có --> P3 --> S7
    S7 -- Có --> S8 --> N1 --> K6
    S7 -- "Không - đơn đã hết hạn" --> S10 --> S11
    S11 -- Có --> S8
    S11 -- Không --> S12 --> P4 --> N2
    S9 --> N2 --> K7
```

### 2.2. Check-in → Sử dụng → Gia hạn → Check-out

```mermaid
flowchart TB
    subgraph KH["👤 KHÁCH HÀNG"]
        K1([Đến trung tâm])
        K2["Quét QR tại cửa"]
        K3["Làm việc"]
        K4{"Muốn<br/>gia hạn?"}
        K5["Chọn thời lượng<br/>& thanh toán gia hạn"]
        K6["Quét QR check-out"]
        K7["Thanh toán phụ phí<br/>online hoặc tại quầy"]
        K8([Ra về])
    end

    subgraph LT["🧑‍💼 LỄ TÂN"]
        L1["Check-in thủ công<br/>tra theo SĐT / Booking ID"]
        L2["Theo dõi bảng vận hành:<br/>sắp hết giờ / overstay"]
        L3["Thu phụ phí &<br/>dịch vụ phát sinh"]
    end

    subgraph HT["🖥 HỆ THỐNG ĐẶT CHỖ"]
        S1{"QR hợp lệ & trong<br/>cửa sổ check-in?"}
        S2["Ghi nhận giờ vào, đơn CHECKED-IN,<br/>vị trí OCCUPIED, mở cửa, cấp Wi-Fi"]
        S3["Hẹn giờ cảnh báo<br/>trước 10/15/30 phút"]
        S4{"Vị trí trống ở<br/>khung kế tiếp?"}
        S5["Cập nhật giờ kết thúc,<br/>tạo lại lịch cảnh báo"]
        S6["Đề xuất vị trí tương đương<br/>/ báo không thể gia hạn"]
        S7["Ghi nhận giờ ra,<br/>đơn CHECKED-OUT"]
        S8{"Quá giờ hoặc còn<br/>dịch vụ chưa trả?"}
        S9["Tính phụ phí theo block<br/>+ dịch vụ phát sinh"]
        S10["Đơn COMPLETED, xuất hóa đơn,<br/>vị trí CẦN DỌN, thu hồi Wi-Fi"]
    end

    subgraph NS["📨 DỊCH VỤ THÔNG BÁO"]
        N1["Nhắc lịch trước 30 phút"]
        N2["Cảnh báo sắp hết giờ<br/>kèm nút Gia hạn"]
    end

    N1 --> K1 --> K2 --> S1
    S1 -- "Không" --> L1 --> S2
    S1 -- "Có" --> S2 --> S3 --> K3
    S3 --> N2 --> K4
    L2 -.-> N2
    K4 -- Có --> K5 --> S4
    S4 -- Có --> S5 --> K3
    S4 -- Không --> S6 --> K6
    K4 -- Không --> K6 --> S7 --> S8
    S8 -- Không --> S10
    S8 -- Có --> S9 --> K7 --> L3 --> S10
    S10 --> K8
```

### 2.3. Xử lý sự cố cơ sở vật chất & đổi chỗ

```mermaid
flowchart TB
    subgraph KH["👤 KHÁCH HÀNG"]
        K1(["Phát hiện sự cố<br/>ổ cắm, điều hòa, vệ sinh, mạng"])
        K2["Báo sự cố trên app<br/>hoặc tại quầy"]
        K3([Tiếp tục làm việc])
        K4([Chuyển sang vị trí mới])
    end

    subgraph LT["🧑‍💼 LỄ TÂN / KỸ THUẬT"]
        L1["Nhận ticket,<br/>kiểm tra tại chỗ"]
        L2{"Khắc phục<br/>ngay được?"}
        L3["Xử lý & đóng ticket"]
        L4["Chọn vị trí mới<br/>lý do: sự cố trung tâm"]
        L5["Sắp xếp lại các<br/>booking tương lai"]
        L6{"Lỗi diện rộng<br/>kéo dài?"}
        L7["Áp dụng đền bù:<br/>đồ uống / voucher / giờ"]
    end

    subgraph HT["🖥 HỆ THỐNG ĐẶT CHỖ"]
        S1["Tạo ticket gắn vị trí,<br/>ưu tiên theo loại"]
        S2["Liệt kê vị trí trống<br/>cùng hạng hoặc cao hơn"]
        S3["Chuyển đơn sang vị trí mới,<br/>miễn chênh lệch nâng hạng,<br/>ghi audit log"]
        S4["Vị trí cũ: BẢO TRÌ<br/>chặn đặt mới"]
        S5["Tìm booking tương lai<br/>bị ảnh hưởng"]
    end

    subgraph NS["📨 DỊCH VỤ THÔNG BÁO"]
        N1["Báo vị trí mới / QR mới"]
        N2["Báo khách tương lai<br/>bị đổi chỗ"]
        N3["Gửi lời xin lỗi<br/>& đền bù"]
    end

    K1 --> K2 --> S1 --> L1 --> L2
    L2 -- Có --> L3 --> L6
    L2 -- Không --> S2 --> L4 --> S3 --> N1 --> K4
    S3 --> S4 --> S5 --> L5 --> N2
    L6 -- Không --> K3
    L6 -- Có --> L7 --> N3 --> K3
```

### 2.4. Đặt chỗ walk-in tại quầy

```mermaid
flowchart TB
    subgraph KH["👤 KHÁCH HÀNG"]
        K1([Đến quầy])
        K2["Nêu nhu cầu: loại chỗ,<br/>thời lượng, số người"]
        K3["Cung cấp SĐT"]
        K4["Thanh toán: tiền mặt /<br/>VietQR / thẻ POS"]
        K5([Nhận QR & vào chỗ])
    end

    subgraph LT["🧑‍💼 LỄ TÂN"]
        L1["Tìm chỗ trống<br/>trên bảng vận hành"]
        L2["Chọn vị trí, khung giờ,<br/>add-on, nhập SĐT"]
        L3["Ghi nhận thanh toán"]
        L4["Check-in cho khách"]
        L5["Chọn vị trí khác /<br/>đề xuất khung giờ khác"]
    end

    subgraph HT["🖥 HỆ THỐNG ĐẶT CHỖ"]
        S1{"Có chỗ trống?"}
        S2{"Soft lock<br/>thành công?"}
        S3["Đơn HELD,<br/>hiển thị số tiền"]
        S4["Đơn CONFIRMED, sinh QR,<br/>xuất biên lai"]
        S5["Đơn CHECKED-IN,<br/>vị trí OCCUPIED"]
    end

    subgraph NS["📨 DỊCH VỤ THÔNG BÁO"]
        N1["Gửi Booking ID + QR"]
    end

    K1 --> K2 --> L1 --> S1
    S1 -- Không --> L5 --> K2
    S1 -- Có --> K3 --> L2 --> S2
    S2 -- "Không - vừa bị đặt online" --> L5
    S2 -- Có --> S3 --> K4 --> L3 --> S4 --> N1 --> L4 --> S5 --> K5
```

---

## 3. State Diagrams

### 3.1. Vòng đời Đơn đặt chỗ (Booking)

```mermaid
stateDiagram-v2
    state "Giữ chỗ tạm<br/>HELD" as Held
    state "Đã xác nhận<br/>CONFIRMED" as Confirmed
    state "Thất bại<br/>FAILED" as Failed
    state "Hết hạn giữ chỗ<br/>EXPIRED" as Expired
    state "Đã check-in<br/>CHECKED_IN" as CheckedIn
    state "Quá giờ<br/>OVERSTAY" as Overstay
    state "Đã check-out<br/>CHECKED_OUT" as CheckedOut
    state "Hoàn tất<br/>COMPLETED" as Completed
    state "Đã hủy<br/>CANCELLED" as Cancelled
    state "Không đến<br/>NO_SHOW" as NoShow
    state "Chờ hoàn tiền<br/>REFUND_PENDING" as RefundPending
    state "Đã hoàn tiền<br/>REFUNDED" as Refunded

    [*] --> Held : Khách bấm Thanh toán / Lễ tân tạo walk-in
    Held --> Confirmed : Thanh toán thành công hoặc tổng tiền bằng 0
    Held --> Failed : Giao dịch bị từ chối
    Held --> Expired : Quá 10 phút chưa thanh toán

    Expired --> Confirmed : Webhook trễ và vị trí còn trống
    Expired --> RefundPending : Webhook trễ và vị trí đã bị đặt
    Expired --> [*] : Không có giao dịch thành công
    Failed --> [*]

    Confirmed --> CheckedIn : Quét QR hợp lệ
    Confirmed --> Cancelled : Khách hủy
    Confirmed --> NoShow : Quá ân hạn không check-in
    Confirmed --> Confirmed : Đổi chỗ / đổi khung giờ

    Cancelled --> RefundPending : Đủ điều kiện hoàn tiền
    Cancelled --> [*] : Không hoàn tiền
    NoShow --> [*]

    CheckedIn --> CheckedIn : Gia hạn / đổi chỗ / gọi dịch vụ
    CheckedIn --> CheckedOut : Check-out trước giờ kết thúc
    CheckedIn --> Overstay : Quá giờ kết thúc + ân hạn
    Overstay --> CheckedIn : Gia hạn thành công
    Overstay --> CheckedOut : Check-out, phát sinh phụ phí

    CheckedOut --> Completed : Đã thanh toán đủ phụ phí và dịch vụ
    Completed --> [*]

    RefundPending --> Confirmed : Khách nhận vị trí tương đương
    RefundPending --> Refunded : Cổng xác nhận hoàn tiền
    Refunded --> [*]

    note right of Held
        Soft lock vị trí, giữ lượt voucher
        và giờ membership trong 5 đến 10 phút
    end note
    note left of Overstay
        Phụ phí = số block vượt
        x đơn giá khung giờ x hệ số
    end note
```

| Trạng thái | Ý nghĩa | Vị trí bị chiếm? |
| :--- | :--- | :---: |
| HELD | Đang giữ chỗ chờ thanh toán | Có (tạm) |
| CONFIRMED | Đã thanh toán, chờ khách đến | Có |
| CHECKED_IN | Khách đang sử dụng | Có |
| OVERSTAY | Quá giờ, chưa check-out | Có |
| CHECKED_OUT | Đã ra, còn khoản chưa thanh toán | Không |
| COMPLETED | Kết thúc, đã thanh toán đủ | Không |
| FAILED / EXPIRED / CANCELLED / NO_SHOW | Kết thúc không sử dụng | Không |
| REFUND_PENDING / REFUNDED | Đang hoàn / đã hoàn tiền | Không |

### 3.2. Trạng thái Vị trí (Resource) theo khung giờ

```mermaid
stateDiagram-v2
    state "Trống<br/>AVAILABLE" as Available
    state "Đang giữ tạm<br/>HELD" as SHeld
    state "Đã đặt<br/>RESERVED" as Reserved
    state "Đang sử dụng<br/>OCCUPIED" as Occupied
    state "Cần dọn dẹp<br/>CLEANING" as Cleaning
    state "Bảo trì<br/>MAINTENANCE" as Maintenance
    state "Ngưng khai thác<br/>INACTIVE" as Inactive

    [*] --> Available : Quản lý thêm vị trí vào sơ đồ
    Available --> SHeld : Khách chuyển sang thanh toán
    SHeld --> Available : Hết hạn giữ chỗ / thanh toán thất bại
    SHeld --> Reserved : Đơn được xác nhận
    Reserved --> Available : Khách hủy / No-show / đổi sang chỗ khác
    Reserved --> Occupied : Khách check-in
    Occupied --> Cleaning : Khách check-out hoặc đổi chỗ
    Cleaning --> Available : Nhân viên dọn xong

    Available --> Maintenance : Báo sự cố / Quản lý đặt bảo trì
    Reserved --> Maintenance : Phát hiện sự cố trước giờ khách đến
    Occupied --> Maintenance : Sự cố khi đang dùng, khách được đổi chỗ
    Cleaning --> Maintenance : Phát hiện hư hỏng khi dọn
    Maintenance --> Available : Khắc phục xong

    Available --> Inactive : Quản lý ngưng khai thác
    Maintenance --> Inactive : Quản lý ngưng khai thác
    Inactive --> Available : Quản lý mở lại
    Inactive --> [*] : Xóa khỏi sơ đồ

    note right of Maintenance
        Không xuất hiện trong tìm kiếm.
        Booking tương lai bị ảnh hưởng
        phải được chuyển sang vị trí khác.
    end note
```

> Trạng thái HELD / RESERVED / OCCUPIED được xác định **theo từng khung giờ** (suy ra từ các booking), còn MAINTENANCE / INACTIVE là trạng thái của bản thân vị trí, áp dụng cho mọi khung giờ trong thời gian hiệu lực.

### 3.3. Trạng thái Giao dịch thanh toán (Payment)

```mermaid
stateDiagram-v2
    state "Khởi tạo<br/>INITIATED" as Initiated
    state "Chờ kết quả<br/>PENDING" as Pending
    state "Thành công<br/>SUCCEEDED" as Succeeded
    state "Thất bại<br/>FAILED" as PFailed
    state "Chưa xác định<br/>UNCONFIRMED" as Unconfirmed
    state "Yêu cầu hoàn tiền<br/>REFUND_REQUESTED" as RefundRequested
    state "Đã hoàn tiền<br/>REFUNDED" as PRefunded
    state "Hoàn tiền lỗi<br/>REFUND_FAILED" as RefundFailed

    [*] --> Initiated : Tạo giao dịch với idempotency key
    Initiated --> Pending : Chuyển khách sang cổng thanh toán
    Pending --> Succeeded : Webhook thành công, chữ ký hợp lệ
    Pending --> PFailed : Bị từ chối / khách hủy
    Pending --> Unconfirmed : Hết thời gian chưa nhận webhook

    Unconfirmed --> Succeeded : Đối soát - cổng xác nhận đã trừ tiền
    Unconfirmed --> PFailed : Đối soát - cổng xác nhận chưa trừ tiền

    Succeeded --> RefundRequested : Hủy đơn / chỗ bị chiếm / đền bù
    RefundRequested --> PRefunded : Cổng hoàn tiền thành công
    RefundRequested --> RefundFailed : Cổng báo lỗi
    RefundFailed --> RefundRequested : Thử lại tự động / nhân viên xử lý

    Succeeded --> [*]
    PFailed --> [*]
    PRefunded --> [*]
```

### 3.4. Trạng thái Ticket sự cố

```mermaid
stateDiagram-v2
    state "Mới<br/>OPEN" as Open
    state "Đang xử lý<br/>IN_PROGRESS" as InProgress
    state "Chờ linh kiện / bên thứ ba<br/>ON_HOLD" as OnHold
    state "Đã xử lý<br/>RESOLVED" as Resolved
    state "Đóng<br/>CLOSED" as Closed

    [*] --> Open : Khách báo trên app / Lễ tân tạo
    Open --> InProgress : Lễ tân nhận và phân công
    InProgress --> Resolved : Khắc phục xong
    InProgress --> OnHold : Không khắc phục ngay, vị trí chuyển Bảo trì
    OnHold --> InProgress : Có linh kiện / kỹ thuật quay lại
    Resolved --> Closed : Khách xác nhận hoặc sau 24 giờ
    Resolved --> InProgress : Sự cố tái diễn
    Closed --> [*]
```
