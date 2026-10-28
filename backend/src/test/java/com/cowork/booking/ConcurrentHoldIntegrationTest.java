package com.cowork.booking;

import com.cowork.booking.application.BookingService;
import com.cowork.booking.application.HoldBookingCommand;
import com.cowork.customer.domain.ContactInfo;
import com.cowork.shared.domain.ConflictException;
import com.cowork.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** NFR-03 / §5.1: nhiều khách cùng giữ một vị trí trong cùng khung giờ thì chỉ một người thành công. */
class ConcurrentHoldIntegrationTest extends IntegrationTest {

    private static final int CUSTOMERS = 10;

    @Autowired
    BookingService bookingService;

    @Test
    void onlyOneOfManySimultaneousHoldsSucceeds() throws Exception {
        LocalDateTime thursday = LocalDateTime.of(2026, 11, 5, 0, 0);
        clock.set(thursday.withHour(8));
        UUID deskId = resource("HD-02").id();

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CUSTOMERS);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < CUSTOMERS; i++) {
            String phone = String.format("09000000%02d", i);
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    bookingService.hold(new HoldBookingCommand(deskId, thursday.withHour(10), thursday.withHour(12), 1,
                            new ContactInfo("Khách " + phone, phone, null)));
                    succeeded.incrementAndGet();
                } catch (ConflictException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(succeeded).hasValue(1);
        assertThat(rejected).hasValue(CUSTOMERS - 1);
    }
}
