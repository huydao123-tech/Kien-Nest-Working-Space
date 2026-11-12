package com.cowork.business.service;

import com.cowork.support.TestCaseLogRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** FR-AUTH-02: mã OTP 6 chữ số ngẫu nhiên. Viết theo {@code .claude/rules/unit-test.md}. */
public class OtpGeneratorTest {

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private final OtpGenerator generator = new OtpGenerator();

    // Viewpoint: VP2
    @Test
    public void test_next_SixDigits_001() {
        // Mã luôn đủ 6 ký tự, chỉ gồm chữ số (kể cả khi số ngẫu nhiên nhỏ hơn 100000 thì có số 0 đứng đầu).
        log.input("count = {}", 200);

        boolean allValid = true;
        for (int i = 0; i < 200; i++) {
            allValid &= generator.next().matches("\\d{6}");
        }

        log.output("allValid = {}", allValid);
        assertTrue(allValid);
    }

    // Viewpoint: VP4
    @Test
    public void test_next_Random_002() {
        // 50 lần gọi cho ra nhiều mã khác nhau (không trả một giá trị cố định).
        log.input("count = {}", 50);

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            codes.add(generator.next());
        }

        log.output("distinctCodes = {}", codes.size() > 1);
        assertEquals(true, codes.size() > 1);
    }
}
