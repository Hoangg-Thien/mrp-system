package mrp.common;

import java.math.BigDecimal;

public final class QuantityFormat {
    private QuantityFormat() { }

    public static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
