package com.xulihang;

import com.atilika.kuromoji.unidic.Token;
import com.atilika.kuromoji.unidic.Tokenizer;
import com.github.houbb.pinyin.util.PinyinHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main {

    private static final com.atilika.kuromoji.ipadic.Tokenizer ipaTokenizer;
    private static final Tokenizer unidicTokenizer;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 片假名到平假名的转换映射表
    private static final String KATAKANA_TO_HIRAGANA =
            "ァアィイゥウェエォオカガキギクグケゲコゴサザシジスズセゼソゾタダチヂッツヅテデトドナニヌネノハバパヒビピフブプヘベペホボポマミムメモャヤュユョヨラリルレロヮワヰヱヲンヴヵヶ";
    private static final String HIRAGANA =
            "ぁあぃいぅうぇえぉおかがきぎくぐけげこごさざしじすずせぜそぞただちぢっつづてでとどなにぬねのはばぱひびぴふぶぷへべぺほぼぽまみむめもゃやゅゆょよらりるれろゎわゐゑをんゔゕゖ";

    static {
        // 初始化两种词典
        ipaTokenizer = new com.atilika.kuromoji.ipadic.Tokenizer.Builder().build();
        unidicTokenizer = new Tokenizer.Builder().build();
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("\n请指定JSON文件路径");
            return;
        }

        String filePath = args[0];
        //String filePath = "test.json";
        processJsonFile(filePath);
    }

    /**
     * 混合分词结果类
     */
    private static class HybridToken {
        String surface;
        String reading;
        String source; // "ipa" 或 "unidic"
        String partOfSpeech2;
        String partOfSpeech3;

        HybridToken(String surface, String reading, String source, String partOfSpeech2, String partOfSpeech3) {
            this.surface = surface;
            this.reading = reading;
            this.source = source;
            this.partOfSpeech2 = partOfSpeech2;
            this.partOfSpeech3 = partOfSpeech3;
        }
    }

    /**
     * 混合分词：最长匹配优先策略，并合并数词+助数词
     */
    private static List<HybridToken> hybridTokenize(String text) {
        // 获取两种分词结果
        List<com.atilika.kuromoji.ipadic.Token> ipaTokens = ipaTokenizer.tokenize(text);
        List<Token> unidicTokens = unidicTokenizer.tokenize(text);

        // 构建位置索引
        Map<Integer, HybridToken> ipaMap = buildIPAMap(ipaTokens);
        Map<Integer, HybridToken> unidicMap = buildUniDicMap(unidicTokens);

        // 使用最长匹配优先算法
        List<HybridToken> result = new ArrayList<>();
        int pos = 0;
        int textLength = text.length();

        while (pos < textLength) {
            HybridToken bestMatch = null;
            int bestLength = 0;

            // 检查当前位置是否有 UniDic 的分词
            HybridToken unidicToken = unidicMap.get(pos);
            if (unidicToken != null && unidicToken.surface.length() > bestLength) {
                bestMatch = unidicToken;
                bestLength = unidicToken.surface.length();
            }
            // 检查当前位置是否有 IPADic 的分词
            HybridToken ipaToken = ipaMap.get(pos);
            if (ipaToken != null && ipaToken.surface.length() > bestLength) {
                bestMatch = ipaToken;
                bestLength = ipaToken.surface.length();
            }

            if (bestMatch != null) {
                result.add(bestMatch);
                pos += bestLength;
            } else {
                // 回退：按字符处理
                result.add(new HybridToken(String.valueOf(text.charAt(pos)),
                        String.valueOf(text.charAt(pos)),
                        "fallback","*","*"));
                pos++;
            }
        }

        // 合并数词和助数词
        result = mergeNumberAndCounter(result);

        return result;
    }

    /**
     * 合并数词和助数词（如：一 + 匹 -> 一匹）
     * 判断条件：前一个词是数词（词性2为"数"），后一个词是助数词（词性3为"助数詞"）
     */
    private static List<HybridToken> mergeNumberAndCounter(List<HybridToken> tokens) {
        if (tokens == null || tokens.size() < 2) {
            return tokens;
        }

        List<HybridToken> merged = new ArrayList<>();
        int i = 0;

        while (i < tokens.size()) {
            HybridToken current = tokens.get(i);

            // 检查是否可以合并：当前是数词，且下一个是助数词
            if (i + 1 < tokens.size()) {
                HybridToken next = tokens.get(i + 1);

                boolean isNumber = isNumberWord(current);
                boolean isCounter = isCounterWord(next);

                if (isNumber && isCounter) {
                    // 合并两个词
                    String mergedSurface = current.surface + next.surface;
                    String mergedReading = getMergedReading(current.reading, next.reading, current.surface, next.surface);
                    String source = current.source + "+" + next.source;

                    HybridToken mergedToken = new HybridToken(
                            mergedSurface,
                            mergedReading,
                            source,
                            "数詞", // 合并后的词性
                            "助数詞"
                    );

                    merged.add(mergedToken);
                    i += 2; // 跳过已合并的两个词
                    continue;
                }
            }

            // 无法合并，直接添加当前词
            merged.add(current);
            i++;
        }

        return merged;
    }

    /**
     * 判断是否为数词
     */
    private static boolean isNumberWord(HybridToken token) {
        if (token == null) return false;

        // 检查词性
        if (token.partOfSpeech2 != null) {
            if (token.partOfSpeech2.equals("数") ||
                    token.partOfSpeech2.contains("数詞") ||
                    token.partOfSpeech2.equals("数量詞")) {
                return true;
            }
        }

        // 检查表面形式是否为数字或常用数词
        String surface = token.surface;
        if (surface.matches("[0-9０-９]+") || // 数字
                surface.matches("[一二三四五六七八九十百千万億兆]+")) { // 汉字数字
            return true;
        }

        return false;
    }

    /**
     * 判断是否为助数词（量词）
     */
    private static boolean isCounterWord(HybridToken token) {
        if (token == null) return false;

        // 检查词性
        if (token.partOfSpeech3 != null) {
            if (token.partOfSpeech3.equals("助数詞") ||
                    token.partOfSpeech3.equals("助数詞一般")) {
                return true;
            }
        }

        if (token.partOfSpeech2 != null) {
            if (token.partOfSpeech2.equals("助数詞")) {
                return true;
            }
        }

        // 常见的助数词表面形式
        String surface = token.surface;
        String[] commonCounters = {"匹", "本", "枚", "冊", "個", "つ", "人", "台", "杯", "足",
                "件", "軒", "階", "回", "皿", "膳", "丁", "錠", "着", "膳"};
        for (String counter : commonCounters) {
            if (surface.equals(counter)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取合并后的读音，处理读音变化（连音、促音化等）
     */
    private static String getMergedReading(String numberReading, String counterReading,
                                           String numberSurface, String counterSurface) {
        if (numberReading == null) numberReading = "";
        if (counterReading == null) counterReading = "";

        // 特殊情况处理
        // 一匹: いち + ひき -> いっぴき
        if (numberSurface.equals("一") && counterSurface.equals("匹")) {
            return "いっぴき";
        }
        // 一冊: いち + さつ -> いっさつ
        if (numberSurface.equals("一") && counterSurface.equals("冊")) {
            return "いっさつ";
        }
        // 一本: いち + ほん -> いっぽん
        if (numberSurface.equals("一") && counterSurface.equals("本")) {
            return "いっぽん";
        }
        // 一杯: いち + はい -> いっぱい
        if (numberSurface.equals("一") && counterSurface.equals("杯")) {
            return "いっぱい";
        }
        // 一階: いち + かい -> いっかい
        if (numberSurface.equals("一") && counterSurface.equals("階")) {
            return "いっかい";
        }
        // 三匹: さん + ひき -> さんびき
        if (numberSurface.equals("三") && counterSurface.equals("匹")) {
            return "さんびき";
        }
        // 三本: さん + ほん -> さんぼん
        if (numberSurface.equals("三") && counterSurface.equals("本")) {
            return "さんぼん";
        }
        // 十本: じゅう + ほん -> じゅっぽん
        if (numberSurface.equals("十") && counterSurface.equals("本")) {
            return "じゅっぽん";
        }

        // 处理促音化
        // 数词以「ち」结尾，助数词以「は」行开头时 → 促音化+半浊音化
        if (numberReading.endsWith("ち") && (counterReading.startsWith("は") || counterReading.startsWith("ひ") ||
                counterReading.startsWith("ふ") || counterReading.startsWith("へ") || counterReading.startsWith("ほ"))) {
            String base = numberReading.substring(0, numberReading.length() - 1);
            String counterModified = counterReading;
            if (counterReading.startsWith("は")) counterModified = "ぱ" + counterReading.substring(1);
            else if (counterReading.startsWith("ひ")) counterModified = "ぴ" + counterReading.substring(1);
            else if (counterReading.startsWith("ふ")) counterModified = "ぷ" + counterReading.substring(1);
            else if (counterReading.startsWith("へ")) counterModified = "ぺ" + counterReading.substring(1);
            else if (counterReading.startsWith("ほ")) counterModified = "ぽ" + counterReading.substring(1);
            return base + "っ" + counterModified;
        }

        // 处理连浊（数词以「ん」结尾，助数词清音变浊音）
        if (numberReading.endsWith("ん") || numberReading.endsWith("n")) {
            String counterModified = counterReading;
            if (counterReading.startsWith("か")) counterModified = "が" + counterReading.substring(1);
            else if (counterReading.startsWith("き")) counterModified = "ぎ" + counterReading.substring(1);
            else if (counterReading.startsWith("く")) counterModified = "ぐ" + counterReading.substring(1);
            else if (counterReading.startsWith("け")) counterModified = "げ" + counterReading.substring(1);
            else if (counterReading.startsWith("こ")) counterModified = "ご" + counterReading.substring(1);
            else if (counterReading.startsWith("さ")) counterModified = "ざ" + counterReading.substring(1);
            else if (counterReading.startsWith("し")) counterModified = "じ" + counterReading.substring(1);
            else if (counterReading.startsWith("す")) counterModified = "ず" + counterReading.substring(1);
            else if (counterReading.startsWith("せ")) counterModified = "ぜ" + counterReading.substring(1);
            else if (counterReading.startsWith("そ")) counterModified = "ぞ" + counterReading.substring(1);
            else if (counterReading.startsWith("た")) counterModified = "だ" + counterReading.substring(1);
            else if (counterReading.startsWith("ち")) counterModified = "ぢ" + counterReading.substring(1);
            else if (counterReading.startsWith("つ")) counterModified = "づ" + counterReading.substring(1);
            else if (counterReading.startsWith("て")) counterModified = "で" + counterReading.substring(1);
            else if (counterReading.startsWith("と")) counterModified = "ど" + counterReading.substring(1);
            else if (counterReading.startsWith("は")) counterModified = "ば" + counterReading.substring(1);
            else if (counterReading.startsWith("ひ")) counterModified = "び" + counterReading.substring(1);
            else if (counterReading.startsWith("ふ")) counterModified = "ぶ" + counterReading.substring(1);
            else if (counterReading.startsWith("へ")) counterModified = "べ" + counterReading.substring(1);
            else if (counterReading.startsWith("ほ")) counterModified = "ぼ" + counterReading.substring(1);
            return numberReading + counterModified;
        }

        // 默认直接拼接
        return numberReading + counterReading;
    }

    /**
     * 构建 IPADic 的位置索引
     */
    private static Map<Integer, HybridToken> buildIPAMap(List<com.atilika.kuromoji.ipadic.Token> tokens) {
        Map<Integer, HybridToken> map = new HashMap<>();
        int pos = 0;
        for (com.atilika.kuromoji.ipadic.Token token : tokens) {
            String surface = token.getSurface();
            String reading = token.getReading();
            if (reading == null || reading.isEmpty()) {
                reading = surface;
            }
            map.put(pos, new HybridToken(surface, katakanaToHiragana(reading), "ipa", token.getPartOfSpeechLevel2(), token.getPartOfSpeechLevel3()));
            pos += surface.length();
        }
        return map;
    }

    /**
     * 构建 UniDic 的位置索引
     */
    private static Map<Integer, HybridToken> buildUniDicMap(List<Token> tokens) {
        Map<Integer, HybridToken> map = new HashMap<>();
        int pos = 0;
        for (Token token : tokens) {
            String surface = token.getSurface();
            String reading = token.getPronunciation();
            if (reading == null || reading.isEmpty()) {
                reading = surface;
            }
            map.put(pos, new HybridToken(surface, katakanaToHiragana(reading), "unidic", token.getPartOfSpeechLevel2(), token.getPartOfSpeechLevel3()));
            pos += surface.length();
        }
        return map;
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
                        sourceWithRuby = addJapaneseRubyHybrid(source);
                    }else if (sourceLang.startsWith("zh")){
                        sourceWithRuby = addChineseRuby(source);
                    }
                    // 为source添加注音
                    boxNode.put("source_markup", sourceWithRuby);

                    // 为target添加注音
                    String targetWithRuby = "";
                    if (targetLang.startsWith("ja")) {
                        targetWithRuby = addJapaneseRubyHybrid(target);
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
     * 使用混合分词器添加日语注音
     */
    private static String addJapaneseRubyHybrid(String text) {
        try {
            List<HybridToken> tokens = hybridTokenize(text);
            StringBuilder result = new StringBuilder();

            for (HybridToken token : tokens) {
                String surface = token.surface;

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

                String readingHiragana = token.reading;
                // 如果读音为空，则不添加ruby
                if (readingHiragana == null || readingHiragana.isEmpty()) {
                    result.append(surface);
                    continue;
                }

                // 获取当前词本身的读音（用于比较）
                String surfaceHiragana = convertToHiragana(surface);

                // 如果读音和原文的平假名表示相同，则不添加ruby
                if (readingHiragana.equals(surfaceHiragana)) {
                    result.append(surface);
                } else {
                    // 添加ruby标签
                    String[] segmented = TextSplitter.splitByCommonPrefixAndSuffix(surfaceHiragana, readingHiragana);
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
}