public class LongestPalindromicSubstring {

    public static String longestPalindrome(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }

        int start = 0, maxLen = 1;

        for (int i = 0; i < s.length(); i++) {
            // 奇数长度回文，中心为 i
            int len1 = expandAroundCenter(s, i, i);
            // 偶数长度回文，中心为 i 和 i+1
            int len2 = expandAroundCenter(s, i, i + 1);
            int len = Math.max(len1, len2);

            if (len > maxLen) {
                maxLen = len;
                // 计算回文起点：中心点向左偏移 (len-1)/2
                start = i - (len - 1) / 2;
            }
        }

        return s.substring(start, start + maxLen);
    }

    private static int expandAroundCenter(String s, int left, int right) {
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            left--;
            right++;
        }
        return right - left - 1;
    }

    public static void main(String[] args) {
        // 测试用例
        String[] tests = {
            "babad",
            "cbbd",
            "a",
            "ac",
            "racecar",
            "abacdfgdcaba"
        };

        for (String t : tests) {
            System.out.printf("输入: \"%s\" -> 输出: \"%s\"%n", t, longestPalindrome(t));
        }
    }
}
