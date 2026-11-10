package com.duynh.shopee.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Máy trạng thái đơn (API_SPEC §9.1): đủ 5 × 5 = 25 cặp (từ, tới).
 * Test đơn vị thuần — không Spring, không DB — nên chạy ở bước `test` trong vài mili-giây.
 * Danh sách cặp hợp lệ ghi tay ở đây, không lấy từ code, để test là một bản đặc tả độc lập với code nó kiểm.
 */
class OrderStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "PENDING>CONFIRMED", "PENDING>CANCELLED",
            "CONFIRMED>SHIPPING", "CONFIRMED>CANCELLED",
            "SHIPPING>DELIVERED");

    static Stream<Arguments> allPairs() {
        return Stream.of(OrderStatus.values())
                .flatMap(from -> Stream.of(OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allPairs")
    void chiDungNamCapDuocPhepChuyen(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.contains(from + ">" + to));
    }
}
