import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Domain Event bằng Java thuần, không có Spring.
 * Chạy:  java learning/DomainEventDemo.java
 */
public class DomainEventDemo {

    // 1. EVENT — chỉ là dữ liệu, báo "việc này ĐÃ xảy ra"
    record PaymentSucceeded(String bookingCode, long amount) {
    }

    // 2. CÁI LOA — giữ danh sách người nghe; hô lên thì gọi lần lượt từng người
    static class Loa {
        private final List<Consumer<PaymentSucceeded>> nguoiNghe = new ArrayList<>();

        void dangKyNghe(Consumer<PaymentSucceeded> nguoi) {
            nguoiNghe.add(nguoi);
        }

        void ho(PaymentSucceeded event) {
            for (Consumer<PaymentSucceeded> nguoi : nguoiNghe) {
                nguoi.accept(event);
            }
        }
    }

    // 3. BÊN HÔ — thanh toán. Để ý: KHÔNG biết Booking hay Notification là ai
    static class PaymentService {
        private final Loa loa;

        PaymentService(Loa loa) {
            this.loa = loa;
        }

        void khachDaTraTien(String bookingCode, long amount) {
            System.out.println("[Payment]      Ghi nhan da nhan " + amount + "d cho don " + bookingCode);
            loa.ho(new PaymentSucceeded(bookingCode, amount));
        }
    }

    public static void main(String[] args) {
        Loa loa = new Loa();

        // 4. NGƯỜI NGHE — mỗi bên tự đăng ký việc mình làm khi nghe thấy
        loa.dangKyNghe(e -> System.out.println("[Booking]      Don " + e.bookingCode() + " -> CONFIRMED"));
        loa.dangKyNghe(e -> System.out.println("[Notification] Gui SMS: dat cho " + e.bookingCode() + " thanh cong"));

        // THỬ: bỏ dấu // ở dòng dưới rồi chạy lại.
        // Có thêm tính năng mới mà KHÔNG sửa một chữ nào trong PaymentService.
        // loa.dangKyNghe(e -> System.out.println("[Diem thuong]  Cong " + e.amount() / 1000 + " diem"));

        new PaymentService(loa).khachDaTraTien("BK123", 100_000);
    }
}
