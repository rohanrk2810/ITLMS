package com.itilms.finance.ledger;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The two-digit GST state codes, by state name. The first two digits of a GSTIN are the state of registration. */
public final class StateCodes {

    private static final Map<String, String> CODES = new HashMap<>();

    static {
        String[][] table = {
                {"jammu and kashmir", "01"}, {"himachal pradesh", "02"}, {"punjab", "03"}, {"chandigarh", "04"},
                {"uttarakhand", "05"}, {"haryana", "06"}, {"delhi", "07"}, {"rajasthan", "08"}, {"uttar pradesh", "09"},
                {"bihar", "10"}, {"sikkim", "11"}, {"arunachal pradesh", "12"}, {"nagaland", "13"}, {"manipur", "14"},
                {"mizoram", "15"}, {"tripura", "16"}, {"meghalaya", "17"}, {"assam", "18"}, {"west bengal", "19"},
                {"jharkhand", "20"}, {"odisha", "21"}, {"chhattisgarh", "22"}, {"madhya pradesh", "23"}, {"gujarat", "24"},
                {"dadra and nagar haveli and daman and diu", "26"}, {"maharashtra", "27"}, {"karnataka", "29"},
                {"goa", "30"}, {"lakshadweep", "31"}, {"kerala", "32"}, {"tamil nadu", "33"}, {"puducherry", "34"},
                {"andaman and nicobar islands", "35"}, {"telangana", "36"}, {"andhra pradesh", "37"}, {"ladakh", "38"}};
        for (String[] row : table) {
            CODES.put(row[0], row[1]);
        }
        CODES.put("orissa", "21");
        CODES.put("pondicherry", "34");
        CODES.put("nct of delhi", "07");
        CODES.put("jammu & kashmir", "01");
        CODES.put("daman and diu", "26");
        CODES.put("dadra and nagar haveli", "26");
    }

    private StateCodes() {
    }

    /** The code for a state name ("Maharashtra" gives "27"), or null when it is empty or not recognised. */
    public static String codeOf(String stateName) {
        if (stateName == null || stateName.isBlank()) {
            return null;
        }
        return CODES.get(stateName.trim().toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("\\s+", " "));
    }

    /** The state name for a code, for "Place of supply: Maharashtra (27)". */
    public static String nameOf(String code) {
        return CODES.entrySet().stream().filter(e -> e.getValue().equals(code)).map(Map.Entry::getKey)
                .filter(n -> !n.equals("orissa") && !n.equals("pondicherry") && !n.equals("nct of delhi")
                        && !n.equals("jammu & kashmir") && !n.equals("daman and diu") && !n.equals("dadra and nagar haveli"))
                .findFirst().map(StateCodes::title).orElse(null);
    }

    private static String title(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.split(" ")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word.equals("and") ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return out.toString();
    }
}
