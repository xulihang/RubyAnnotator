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

            // 处理长音符号
            if (c == 'ー' && result.length() > 0) {
                char prev = result.charAt(result.length() - 1);

                // 检查前一个字符是否是小写假名（拗音部分）
                boolean isSmallKana = (prev == 'ゃ' || prev == 'ゅ' || prev == 'ょ' ||
                        prev == 'ぁ' || prev == 'ぃ' || prev == 'ぅ' ||
                        prev == 'ぇ' || prev == 'ぉ');

                // 如果前一个是小写假名，需要看再前一个假名来确定段
                if (isSmallKana && result.length() >= 2) {
                    char prevPrev = result.charAt(result.length() - 2);
                    String prevPrevStr = String.valueOf(prevPrev);
                    String prevStr = String.valueOf(prev);

                    // 根据拗音的组合决定长音对应的假名
                    if (prevStr.equals("ゃ")) {
                        // きゃ、しゃ、ちゃ、にゃ、ひゃ、みゃ、りゃ、ぎゃ、じゃ、びゃ、ぴゃ
                        if (prevPrevStr.matches("[きしちにひみりぎじびぴ]")) {
                            result.append('あ');  // きゃー → きゃあ？不对，应该是きゃあ？
                            // 实际上きゃー通常转换为きゃあ，但口语中有时是きゃー保持原样
                        }
                    } else if (prevStr.equals("ゅ")) {
                        // きゅ、しゅ、ちゅ、にゅ、ひゅ、みゅ、りゅ、ぎゅ、じゅ、びゅ、ぴゅ
                        if (prevPrevStr.matches("[きしちにひみりぎじびぴ]")) {
                            result.append('う');  // きゅー → きゅう
                        }
                    } else if (prevStr.equals("ょ")) {
                        // きょ、しょ、ちょ、にょ、ひょ、みょ、りょ、ぎょ、じょ、びょ、ぴょ
                        if (prevPrevStr.matches("[きしちにひみりぎじびぴ]")) {
                            result.append('う');  // きょー → きょう
                        }
                    } else {
                        // 其他小写假名的处理
                        result.append(getLongVowelForSmallKana(prev, prevPrev));
                    }
                } else {
                    // 正常的长音处理
                    result.append(getLongVowelForNormalKana(prev));
                }
                continue;
            }

            // 处理促音（小写ッ）
            if (c == 'ッ' && i + 1 < katakana.length()) {
                result.append('っ');
                continue;
            }

            // 普通片假名转换
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
     * 获取普通假名对应的长音假名
     */
    private static char getLongVowelForNormalKana(char prev) {
        if (prev == 'あ' || prev == 'か' || prev == 'さ' || prev == 'た' || prev == 'な' ||
                prev == 'は' || prev == 'ま' || prev == 'や' || prev == 'ら' || prev == 'わ' ||
                prev == 'が' || prev == 'ざ' || prev == 'だ' || prev == 'ば' || prev == 'ぱ') {
            return 'あ';
        } else if (prev == 'い' || prev == 'き' || prev == 'し' || prev == 'ち' || prev == 'に' ||
                prev == 'ひ' || prev == 'み' || prev == 'り' || prev == 'ぎ' || prev == 'じ' ||
                prev == 'ぢ' || prev == 'び' || prev == 'ぴ') {
            return 'い';
        } else if (prev == 'う' || prev == 'く' || prev == 'す' || prev == 'つ' || prev == 'ぬ' ||
                prev == 'ふ' || prev == 'む' || prev == 'ゆ' || prev == 'る' || prev == 'ぐ' ||
                prev == 'ず' || prev == 'づ' || prev == 'ぶ' || prev == 'ぷ') {
            return 'う';
        } else if (prev == 'え' || prev == 'け' || prev == 'せ' || prev == 'て' || prev == 'ね' ||
                prev == 'へ' || prev == 'め' || prev == 'れ' || prev == 'げ' || prev == 'ぜ' ||
                prev == 'で' || prev == 'べ' || prev == 'ぺ') {
            return 'い';
        } else if (prev == 'お' || prev == 'こ' || prev == 'そ' || prev == 'と' || prev == 'の' ||
                prev == 'ほ' || prev == 'も' || prev == 'よ' || prev == 'ろ' || prev == 'ご' ||
                prev == 'ぞ' || prev == 'ど' || prev == 'ぼ' || prev == 'ぽ') {
            return 'う';
        }
        return 'ー';
    }

    /**
     * 获取拗音长音对应的假名
     */
    private static char getLongVowelForSmallKana(char smallKana, char prevPrev) {
        String prevPrevStr = String.valueOf(prevPrev);

        switch (smallKana) {
            case 'ゃ':
                // ゃ段长音通常接あ
                return 'あ';
            case 'ゅ':
                // ゅ段长音接う
                return 'う';
            case 'ょ':
                // ょ段长音接う
                return 'う';
            case 'ぁ':
                return 'あ';
            case 'ぃ':
                return 'い';
            case 'ぅ':
                return 'う';
            case 'ぇ':
                return 'い';
            case 'ぉ':
                return 'う';
            default:
                return 'ー';
        }
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