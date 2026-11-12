package com.cowork.support;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ghi log unit test vào {@code UTLogs/<tên class test>.log} ở thư mục gốc repo, mỗi class test một file, mỗi testcase đúng 5 dòng:
 * tên hàm test, phiên bản JDK, input, output, result. File được ghi mới ở mỗi lần chạy.
 * Từng hàm test tự gọi {@link #input} và {@link #output}, quy tắc ở {@code .claude/rules/unit-test.md} mục 6.
 */
public class TestCaseLogRule extends TestWatcher {

    private static final Path LOG_DIRECTORY = repositoryRoot().resolve("UTLogs");
    private static final String PLACEHOLDER = "{}";
    private static final String VALUE_SEPARATOR = ", ";
    private static final Set<Path> RESET_FILES = new HashSet<>();

    private final List<String> inputs = new ArrayList<>();
    private final List<String> outputs = new ArrayList<>();
    private String testName;
    private Path logFile;

    /** Ghi tham số của hàm được test, ví dụ {@code log.input("request = {}", request)}. */
    public void input(String template, Object... values) {
        inputs.add(format(template, values));
    }

    /** Ghi giá trị testcase muốn thể hiện, gọi trước assert, ví dụ {@code log.output("email = {}", email)}. */
    public void output(String template, Object... values) {
        outputs.add(format(template, values));
    }

    @Override
    protected void starting(Description description) {
        testName = description.getMethodName();
        logFile = LOG_DIRECTORY.resolve(description.getTestClass().getSimpleName() + ".log");
    }

    @Override
    protected void succeeded(Description description) {
        write("PASS");
    }

    @Override
    protected void failed(Throwable e, Description description) {
        write("FAIL");
    }

    private static String format(String template, Object[] values) {
        StringBuilder text = new StringBuilder();
        int start = 0;
        for (Object value : values) {
            int position = template.indexOf(PLACEHOLDER, start);
            if (position < 0) {
                throw new IllegalArgumentException("Số giá trị nhiều hơn số {} trong: " + template);
            }
            text.append(template, start, position).append(value);
            start = position + PLACEHOLDER.length();
        }
        if (template.indexOf(PLACEHOLDER, start) >= 0) {
            throw new IllegalArgumentException("Số giá trị ít hơn số {} trong: " + template);
        }
        return text.append(template.substring(start)).toString();
    }

    private void write(String result) {
        append(logFile, List.of(
                "test: " + testName,
                "jdk: " + System.getProperty("java.version"),
                "input: " + oneLine(inputs),
                "output: " + oneLine(outputs),
                "result: " + result));
    }

    /** Thư mục gần nhất có {@code .git}, để chạy từ {@code backend} hay từ gốc repo đều ghi cùng một chỗ. */
    private static Path repositoryRoot() {
        Path start = Path.of("").toAbsolutePath();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(".git"))) {
                return dir;
            }
        }
        return start;
    }

    private static String oneLine(List<String> values) {
        return String.join(VALUE_SEPARATOR, values).replaceAll("\\R", " ");
    }

    private static synchronized void append(Path file, List<String> lines) {
        try {
            Files.createDirectories(LOG_DIRECTORY);
            if (RESET_FILES.add(file)) {
                Files.deleteIfExists(file);
            }
            Files.write(file, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
