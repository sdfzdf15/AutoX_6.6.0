package org.autojs.autojs.model.autocomplete;

import java.util.Arrays;

/**
 * Created by Stardust on 2017/9/28.
 */

public class Symbols {

    private static CodeCompletions sSymbols = CodeCompletions.just(Arrays.asList(
            "\"", "\"\"", ",", ".", "/", "*", "//",
            "(", ")", "()",
            "'", "''", ":", ";",
            "|", "||",
            "[", "]", "[]", "{", "}", "{}",
            "=", "==", "===",
            "!", "&", "&&", "_",
            "+", "++", "-", "--", "<", ">",
            "?", "$", "#", "\"#\"", "^", "。",
            "`", "@", "%", "《", "》", "《》", "±", "≠", "‰",
            "function ",
            "var ", "const ", "typeof ",
            "if", "else", "switch", "case", "break", "default",
            "for", "int", "i", "j", "while", "true", "false",
            "do", "while", "continue", "return",
            "try {  } catch (err) {  }",
            "try {  } catch(err) {  } finally { }",
            "module.exports = ", "require();",
            "importClass()", "importPackage()",
            "∞", "∫", "∑", "π", "‧", "…",
            "~", "¥", "©", "®", "™", "°",
            "✓", "✔", "✗", "✘", "↺", "↻",
            "→", "↑", "→", "↓",
            "☐", "☑", "︽", "︾", "︿", "﹀",
            "❤", "✩", "✪", "☹", "☺", "☻",
            "⓿", "❶", "❷", "❸", "❹", "❺", "❻", "❼", "❽", "❾", "❿",
            "ａ", "ｂ", "ｃ", "ｄ", "ｅ", "ｆ", "ｇ", "ｈ", "ｉ", "ｊ", "ｋ", "ｌ", "ｍ", "ｎ", "ｏ", "ｐ", "ｑ", "ｒ", "ｓ", "ｔ", "ｕ", "ｖ", "ｗ", "ｘ", "ｙ", "ｚ",
            "Ａ", "Ｂ", "Ｃ", "Ｄ", "Ｅ", "Ｆ", "Ｇ", "Ｈ", "Ｉ", "Ｊ", "Ｋ", "Ｌ", "Ｍ", "Ｎ", "Ｏ", "Ｐ", "Ｑ", "Ｒ", "Ｓ", "Ｔ", "Ｕ", "Ｖ", "Ｗ", "Ｘ", "Ｙ", "Ｚ",
            "Ⓐ", "Ⓑ", "Ⓒ", "Ⓓ", "Ⓔ", "Ⓕ", "Ⓖ", "Ⓗ", "Ⓘ", "Ⓙ", "Ⓚ", "Ⓛ", "Ⓜ", "Ⓝ", "Ⓞ", "Ⓟ", "Ⓠ", "Ⓡ", "Ⓢ", "Ⓣ", "Ⓤ", "Ⓥ", "Ⓦ", "Ⓧ", "Ⓨ", "Ⓩ"
    ));

    public static CodeCompletions getSymbols() {
        return sSymbols;
    }
}
