package com.xulihang;

public class TextSplitter {

    /**
     * 获取字符串末尾的连续相同字符序列
     * 例如："食べべ" -> "べべ"（因为最后两个字符都是'べ'）
     *      "たべべ" -> "べべ"
     *      "abc" -> "c"
     *      "abcc" -> "cc"
     */
    private static String getSuffixSequence(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        char lastChar = str.charAt(str.length() - 1);
        int count = 1;

        // 从末尾向前查找连续相同字符
        for (int i = str.length() - 2; i >= 0; i--) {
            if (str.charAt(i) == lastChar) {
                count++;
            } else {
                break;
            }
        }

        return str.substring(str.length() - count);
    }

    /**
     * 比较两个字符串末尾的连续字符序列，如果相同则分割
     * @param text1 第一个文本
     * @param text2 第二个文本
     * @return 分割后的结果数组 [text1Prefix, text1Suffix, text2Prefix, text2Suffix]
     *         如果末尾连续字符序列不同，返回null
     */
    public static String[] splitBySameEndingSequence(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return null;
        }

        // 获取末尾连续相同字符序列
        String suffix1 = getSuffixSequence(text1);
        String suffix2 = getSuffixSequence(text2);

        //System.out.println("【分析】");
        //System.out.println("  \"" + text1 + "\" 末尾连续字符序列: \"" + suffix1 + "\"");
        //System.out.println("  \"" + text2 + "\" 末尾连续字符序列: \"" + suffix2 + "\"");

        // 检查末尾序列是否相同
        if (!suffix1.equals(suffix2)) {
            //System.out.println("  → 末尾序列不同，无法分割\n");
            return new String[]{text1, "", text2, ""};
        }

        //System.out.println("  → 末尾序列相同，进行分割\n");

        // 分割文本1：去掉末尾序列
        String prefix1 = text1.substring(0, text1.length() - suffix1.length());

        // 分割文本2：去掉末尾序列
        String prefix2 = text2.substring(0, text2.length() - suffix2.length());

        return new String[]{prefix1, suffix1, prefix2, suffix2};
    }

    public static void main(String[] args) {
        System.out.println("=== 测试1：食べべ 和 たべべ ===");
        String text1 = "食べべ";
        String text2 = "たべべ";

        String[] result = splitBySameEndingSequence(text1, text2);

        if (result != null) {
            System.out.println("【分割结果】");
            System.out.println("  第一个文本: \"" + result[0] + "\" + \"" + result[1] + "\"");
            System.out.println("  第二个文本: \"" + result[2] + "\" + \"" + result[3] + "\"");
        } else {
            System.out.println("无法分割");
        }

        System.out.println("\n=== 测试2：食べ 和 たべ ===");
        String[] result2 = splitBySameEndingSequence("食べ", "たべ");
        if (result2 != null) {
            System.out.println("【分割结果】");
            System.out.println("  第一个文本: \"" + result2[0] + "\" + \"" + result2[1] + "\"");
            System.out.println("  第二个文本: \"" + result2[2] + "\" + \"" + result2[3] + "\"");
        } else {
            System.out.println("无法分割");
        }

        System.out.println("\n=== 测试3：食べべべ 和 たべべ ===");
        String[] result3 = splitBySameEndingSequence("食べべべ", "たべべ");
        if (result3 != null) {
            System.out.println("【分割结果】");
            System.out.println("  第一个文本: \"" + result3[0] + "\" + \"" + result3[1] + "\"");
            System.out.println("  第二个文本: \"" + result3[2] + "\" + \"" + result3[3] + "\"");
        } else {
            System.out.println("无法分割");
        }
    }
}