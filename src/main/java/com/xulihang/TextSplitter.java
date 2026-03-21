package com.xulihang;

public class TextSplitter {

    /**
     * 获取两个字符串的最长公共前缀
     */
    private static String getCommonPrefix(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return "";
        }

        int minLength = Math.min(str1.length(), str2.length());
        int i = 0;
        while (i < minLength && str1.charAt(i) == str2.charAt(i)) {
            i++;
        }
        return str1.substring(0, i);
    }

    /**
     * 获取两个字符串的最长公共后缀
     */
    private static String getCommonSuffix(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return "";
        }

        int len1 = str1.length();
        int len2 = str2.length();
        int i = 0;
        while (i < len1 && i < len2 &&
                str1.charAt(len1 - 1 - i) == str2.charAt(len2 - 1 - i)) {
            i++;
        }
        return str1.substring(len1 - i);
    }

    /**
     * 分割两个字符串的公共前缀和公共后缀
     * @param text1 第一个文本
     * @param text2 第二个文本
     * @return 分割后的结果数组 [text1Prefix, text1Middle, text1Suffix, text2Prefix, text2Middle, text2Suffix]
     */
    public static String[] splitByCommonPrefixAndSuffix(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return null;
        }

        // 获取最长公共前缀
        String commonPrefix = getCommonPrefix(text1, text2);

        // 获取最长公共后缀（在去掉公共前缀后的字符串中查找）
        String remaining1 = text1.substring(commonPrefix.length());
        String remaining2 = text2.substring(commonPrefix.length());
        String commonSuffix = getCommonSuffix(remaining1, remaining2);

        // 分割文本1
        String prefix1 = commonPrefix;
        String suffix1 = commonSuffix;
        String middle1 = remaining1.substring(0, remaining1.length() - commonSuffix.length());

        // 分割文本2
        String prefix2 = commonPrefix;
        String suffix2 = commonSuffix;
        String middle2 = remaining2.substring(0, remaining2.length() - commonSuffix.length());

        return new String[]{prefix1, middle1, suffix1, prefix2, middle2, suffix2};
    }

    public static void main(String[] args) {
        System.out.println("=== 测试1：お尋 和 おたず ===");
        String text1 = "お尋ず";
        String text2 = "おたずず";

        String[] result = splitByCommonPrefixAndSuffix(text1, text2);

        if (result != null) {
            System.out.println("【分割结果】");
            System.out.println("  第一个文本: \"" + result[0] + "\" + \"" + result[1] + "\" + \"" + result[2] + "\"");
            System.out.println("  第二个文本: \"" + result[3] + "\" + \"" + result[4] + "\" + \"" + result[5] + "\"");
        }

        System.out.println("\n=== 测试2：食べべ 和 たべべ ===");
        String[] result2 = splitByCommonPrefixAndSuffix("食べべ", "たべべ");
        if (result2 != null) {
            System.out.println("【分割结果】");
            System.out.println("  第一个文本: \"" + result2[0] + "\" + \"" + result2[1] + "\" + \"" + result2[2] + "\"");
            System.out.println("  第二个文本: \"" + result2[3] + "\" + \"" + result2[4] + "\" + \"" + result2[5] + "\"");
        }

        System.out.println("\n=== 测试3：おお尋 和 おおたず ===");
        String[] result3 = splitByCommonPrefixAndSuffix("おお尋", "おおたず");
        if (result3 != null) {
            System.out.println("【分割结果】");
            System.out.println("  第一个文本: \"" + result3[0] + "\" + \"" + result3[1] + "\" + \"" + result3[2] + "\"");
            System.out.println("  第二个文本: \"" + result3[3] + "\" + \"" + result3[4] + "\" + \"" + result3[5] + "\"");
        }

        System.out.println("\n=== 测试4：食べべべ 和 たべべ ===");
        String[] result4 = splitByCommonPrefixAndSuffix("食べべべ", "たべべ");
        if (result4 != null) {
            System.out.println("【分割结果】");
            System.out.println("  第一个文本: \"" + result4[0] + "\" + \"" + result4[1] + "\" + \"" + result4[2] + "\"");
            System.out.println("  第二个文本: \"" + result4[3] + "\" + \"" + result4[4] + "\" + \"" + result4[5] + "\"");
        }

        System.out.println("\n=== 测试5：abcxyz 和 ab123xyz ===");
        String[] result5 = splitByCommonPrefixAndSuffix("abcxyz", "ab123xyz");
        if (result5 != null) {
            System.out.println("【分割结果】");
            System.out.println("  第一个文本: \"" + result5[0] + "\" + \"" + result5[1] + "\" + \"" + result5[2] + "\"");
            System.out.println("  第二个文本: \"" + result5[3] + "\" + \"" + result5[4] + "\" + \"" + result5[5] + "\"");
        }
    }
}