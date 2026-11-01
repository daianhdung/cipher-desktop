package common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Đếm số lần xuất hiện của các chữ cái trong bản rõ (yêu cầu Server của đề tài 8)
 * và định dạng kết quả để gửi về Client.
 *
 * Định dạng dây: {@code "a=3;b=1;..."} — sắp giảm dần theo số lần, bằng nhau thì
 * theo thứ tự chữ. Client parse lại thành Map để đổ bảng. Dùng chuỗi phẳng
 * thay object serialization để bạn làm Server bằng ngôn ngữ/JDK nào cũng đọc được.
 *
 * "Chữ cái" = {@link Character#isLetter}, gộp hoa/thường về thường
 * ("Xin Chào" -> x=1, i=1, n=1, c=1, h=1, à=1, o=1). Chữ số, dấu cách, dấu câu
 * bị bỏ qua. Chữ Việt có dấu được giữ nguyên là một chữ cái riêng.
 */
public final class LetterFrequency {

    private LetterFrequency() {
    }

    public static Map<Character, Integer> demMap(String banRo) {
        Map<Character, Integer> dem = new TreeMap<>();
        for (int i = 0; i < banRo.length(); i++) {
            char c = banRo.charAt(i);
            if (Character.isLetter(c)) {
                dem.merge(Character.toLowerCase(c), 1, Integer::sum);
            }
        }
        return dem;
    }

    /** Đếm rồi định dạng thành chuỗi gửi đi. */
    public static String dem(String banRo) {
        return dinhDang(demMap(banRo));
    }

    public static String dinhDang(Map<Character, Integer> dem) {
        List<Map.Entry<Character, Integer>> ds = new ArrayList<>(dem.entrySet());
        ds.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : Character.compare(a.getKey(), b.getKey());
        });
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Character, Integer> e : ds) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** Parse chuỗi "a=3;b=1" giữ nguyên thứ tự Server đã sắp. */
    public static LinkedHashMap<Character, Integer> parse(String s) {
        LinkedHashMap<Character, Integer> kq = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) {
            return kq;
        }
        for (String phan : s.split(";")) {
            int eq = phan.indexOf('=');
            if (eq == 1) {
                try {
                    kq.put(phan.charAt(0), Integer.parseInt(phan.substring(2)));
                } catch (NumberFormatException ignore) {
                    // bỏ phần tử hỏng, không làm hỏng cả bảng
                }
            }
        }
        return kq;
    }

    public static int tongChuCai(Map<Character, Integer> dem) {
        int t = 0;
        for (int v : dem.values()) {
            t += v;
        }
        return t;
    }
}
