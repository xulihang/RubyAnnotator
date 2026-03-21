package com.xulihang;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
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
        //if (args.length < 1) {
        //    System.out.println("请指定JSON文件路径");
        //    return;
        //}

        //String filePath = args[0];
        String filePath = "test.json";
        processJsonFile(filePath);
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
                    }else if (sourceLang.startsWith("zh")){
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
        String reading = token.getReading();
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
        String reading = token.getReading();
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
                    String[] segmented = TextSplitter.splitBySameEndingSequence(surfaceHiragana,readingHiragana);
                    result.append("<ruby>")
                            .append(segmented[0])
                            .append("<rt>")
                            .append(segmented[2])
                            .append("</rt></ruby>");
                    result.append(segmented[1]);
                }
            }

            return result.toString();
        } catch (Exception e) {
            System.err.println("处理日语文本时出错: " + e.getMessage());
            return text;
        }
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