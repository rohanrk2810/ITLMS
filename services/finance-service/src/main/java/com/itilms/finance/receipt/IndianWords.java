package com.itilms.finance.receipt;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** An amount in words the Indian way: "Rupees Fifteen Thousand Only", with lakh and crore. */
final class IndianWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private IndianWords() {
    }

    static String rupees(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = rounded.toBigInteger().longValue();
        int paise = rounded.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        StringBuilder sb = new StringBuilder("Rupees ").append(rupees == 0 ? "Zero" : words(rupees));
        if (paise > 0) {
            sb.append(" and ").append(words(paise)).append(" Paise");
        }
        return sb.append(" Only").toString();
    }

    static String words(long n) {
        if (n == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        n %= 10_000_000;
        long lakh = n / 100_000;
        n %= 100_000;
        long thousand = n / 1_000;
        n %= 1_000;
        long hundred = n / 100;
        long rest = n % 100;
        if (crore > 0) {
            sb.append(words(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            sb.append(two(lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            sb.append(two(thousand)).append(" Thousand ");
        }
        if (hundred > 0) {
            sb.append(ONES[(int) hundred]).append(" Hundred ");
        }
        if (rest > 0) {
            if (sb.length() > 0) {
                sb.append("and ");
            }
            sb.append(two(rest));
        }
        return sb.toString().trim();
    }

    private static String two(long n) {
        if (n < 20) {
            return ONES[(int) n];
        }
        return (TENS[(int) (n / 10)] + (n % 10 == 0 ? "" : " " + ONES[(int) (n % 10)])).trim();
    }
}
