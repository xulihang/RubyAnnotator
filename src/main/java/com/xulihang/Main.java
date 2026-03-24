package com.xulihang;

import com.atilika.kuromoji.unidic.Token;
import com.atilika.kuromoji.unidic.Tokenizer;
import com.github.houbb.pinyin.util.PinyinHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Main {

    private static final Tokenizer tokenizer = new Tokenizer();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 片假名到平假名的转换映射表
    private static final String KATAKANA_TO_HIRAGANA =
            "ァアィイゥウェエォオカガキギクグケゲコゴサザシジスズセゼソゾタダチヂッツヅテデトドナニヌネノハバパヒビピフブプヘベペホボポマミムメモャヤュユョヨラリルレロヮワヰヱヲンヴヵヶ";
    private static final String HIRAGANA =
            "ぁあぃいぅうぇえぉおかがきぎくぐけげこごさざしじすずせぜそぞただちぢっつづてでとどなにぬねのはばぱひびぴふぶぷへべぺほぼぽまみむめもゃやゅゆょよらりるれろゎわゐゑをんゔゕゖ";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("请指定JSON文件路径");
            return;
        }

        String filePath = args[0];
        //String filePath = "test.json";
        processJsonFile(filePath);
        //Tokenizer tokenizer = new Tokenizer() ;
        //List<Token> tokens = tokenizer.tokenize("じゃーこの話あんたにゃ関係ないか。");
        //for (Token token : tokens) {
        //   System.out.println(token.getSurface() + "\t" + token.getAllFeatures());
        //}
    }

    private static void processJsonFile(String filePath) {
        try {
            // 读取JSON文件
            File jsonFile = new File(filePath);
            ObjectNode rootNode = (ObjectNode) objectMapper.readTree(jsonFile);

            // 获取boxes数组
            String sourceLang = rootNode.get("source").asText();
            String targetLang = rootNode.get("target").asText();
            ArrayNode boxesNode = (ArrayNode) rootNode.get("boxes");
            if (boxesNode != null) {
                for (int i = 0; i < boxesNode.size(); i++) {
                    ObjectNode boxNode = (ObjectNode) boxesNode.get(i);
                    String source = boxNode.get("source").asText();
                    String target = boxNode.get("target").asText();
                    String sourceWithRuby = "";
                    if (sourceLang.startsWith("ja")) {
                        sourceWithRuby = addJapaneseRuby(source);
                    }else if (sourceLang.startsWith("zh")){
                        sourceWithRuby = addChineseRuby(source);
                    }
                    // 为source添加注音
                    boxNode.put("source_markup", sourceWithRuby);

                    // 为target添加注音
                    String targetWithRuby = "";
                    if (targetLang.startsWith("ja")) {
                        targetWithRuby = addJapaneseRuby(target);
                    }else if (targetLang.startsWith("zh")){
                        targetWithRuby = addChineseRuby(target);
                    }
                    boxNode.put("target_markup", targetWithRuby);
                }
            }

            // 保存回原文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, rootNode);
            System.out.println("处理完成，已保存到: " + filePath);

        } catch (IOException e) {
            System.err.println("处理文件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * 判断字符串是否包含汉字
     */
    private static boolean isKanji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 汉字的Unicode范围：CJK统一表意文字
            if ((c >= '\u4E00' && c <= '\u9FFF') ||  // 基本汉字
                    (c >= '\u3400' && c <= '\u4DBF') ||  // 扩展A
                    (c >= '\uF900' && c <= '\uFAFF')) {  // 兼容汉字
                return true;
            }
        }
        return false;
    }
    /**
     * 将片假名转换为平假名
     */
    private static String katakanaToHiragana(String katakana) {
        if (katakana == null || katakana.isEmpty()) {
            return katakana;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < katakana.length(); i++) {
            char c = katakana.charAt(i);
            int index = KATAKANA_TO_HIRAGANA.indexOf(c);
            if (index >= 0) {
                result.append(HIRAGANA.charAt(index));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 获取词的平假名读音
     */
    private static String getReadingHiragana(Token token) {
        String reading = token.getPronunciation();
        if (reading == null || reading.isEmpty()) {
            return null;
        }
        return katakanaToHiragana(reading);
    }

    /**
     * 获取原文的平假名表示（用于比较）
     */
    private static String getSurfaceHiragana(Token token) {
        String surface = token.getSurface();
        // 对于已经是平假名、片假名的部分，直接转换
        // 对于汉字部分，需要获取其读音
        String reading = token.getPronunciation();
        if (reading != null && !reading.isEmpty()) {
            return katakanaToHiragana(reading);
        }
        return surface;
    }

    private static String addJapaneseRuby(String text) {
        try {
            List<Token> tokens = tokenizer.tokenize(text);
            StringBuilder result = new StringBuilder();

            for (Token token : tokens) {
                String surface = token.getSurface();

                // 跳过标点符号和空格
                if (isJapanesePunctuation(surface)) {
                    result.append(surface);
                    continue;
                }

                // 如果不是汉字，跳过注音
                if (!isKanji(surface)) {
                    result.append(surface);
                    continue;
                }

                String readingHiragana = getReadingHiragana(token);
                // 如果读音为空，则不添加ruby
                if (readingHiragana == null || readingHiragana.isEmpty() || readingHiragana.equals("*")) {
                    result.append(surface);
                    continue;
                }

                // 获取当前词本身的读音（用于比较）
                // 如果这个词完全是假名（平假名或片假名），那么surfaceHiragana应该等于surface转换后的结果
                String surfaceHiragana = convertToHiragana(surface);

                // 如果读音和原文的平假名表示相同，则不添加ruby
                if (readingHiragana.equals(surfaceHiragana)) {
                    result.append(surface);
                } else {
                    // 添加ruby标签
                    String[] segmented = TextSplitter.splitByCommonPrefixAndSuffix(surfaceHiragana,readingHiragana);
                    result.append(segmented[0]);
                    result.append("<ruby>")
                            .append(segmented[1])
                            .append("<rt>")
                            .append(segmented[4])
                            .append("</rt></ruby>");
                    result.append(segmented[2]);
                }
            }

            return result.toString();
        } catch (Exception e) {
            System.err.println("处理日语文本时出错: " + e.getMessage());
            return text;
        }
    }

    /**
     * 判断字符串是否全部由假名（平假名或片假名）组成
     */
    private static boolean isKana(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 检查是否为平假名或片假名
            if (!isHiragana(c) && !isKatakana(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符是否为平假名
     */
    private static boolean isHiragana(char c) {
        // 平假名的Unicode范围：\u3040-\u309F
        return c >= '\u3040' && c <= '\u309F';
    }

    /**
     * 判断字符是否为片假名
     */
    private static boolean isKatakana(char c) {
        // 片假名的Unicode范围：\u30A0-\u30FF
        return c >= '\u30A0' && c <= '\u30FF';
    }

    /**
     * 将文本转换为平假名（仅转换片假名，汉字保持原样）
     */
    private static String convertToHiragana(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = KATAKANA_TO_HIRAGANA.indexOf(c);
            if (index >= 0) {
                result.append(HIRAGANA.charAt(index));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String addChineseRuby(String text) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String chStr = String.valueOf(ch);

            // 跳过标点符号和空格
            if (isChinesePunctuation(ch) || Character.isWhitespace(ch)) {
                result.append(ch);
                continue;
            }

            // 获取拼音
            try {
                String pinyin = PinyinHelper.toPinyin(chStr);
                if (pinyin != null && !pinyin.isEmpty() && !pinyin.equals(chStr)) {
                    // 将拼音转换为小写并去除音调数字
                    pinyin = pinyin.toLowerCase().replaceAll("\\d", "");
                    result.append("<ruby>")
                            .append(ch)
                            .append("<rt>")
                            .append(pinyin)
                            .append("</rt></ruby>");
                } else {
                    result.append(ch);
                }
            } catch (Exception e) {
                result.append(ch);
            }
        }

        return result.toString();
    }

    private static boolean isJapanesePunctuation(String text) {
        return text.matches("[\\p{Punct}\\s]+") ||
                text.matches("[、。！？「」『』【】（）]+");
    }

    private static boolean isChinesePunctuation(char c) {
        return c == '，' || c == '。' || c == '！' || c == '？' ||
                c == '；' || c == '：' || c == '“' || c == '”' ||
                c == '‘' || c == '’' || c == '（' || c == '）' ||
                c == '《' || c == '》' || c == '、';
    }
}