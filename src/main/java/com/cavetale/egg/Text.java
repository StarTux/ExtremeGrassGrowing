package com.cavetale.egg;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.bukkit.ChatColor;

final class Text {
    private Text() { }

    public static List<String> wrap(@NonNull String what,
                                    final int maxLineLength) {
        String[] words = what.split("\\s+");
        List<String> lines = new ArrayList<>();
        if (words.length == 0) return lines;
        StringBuilder line = new StringBuilder(words[0]);
        int lineLength = ChatColor.stripColor(words[0]).length();
        for (int i = 1; i < words.length; ++i) {
            String word = words[i];
            int wordLength = ChatColor.stripColor(word).length();
            if (lineLength + wordLength + 1 > maxLineLength) {
                lines.add(line.toString());
                line = new StringBuilder(word);
                lineLength = wordLength;
            } else {
                line.append(" ");
                line.append(word);
                lineLength += wordLength + 1;
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    public static List<String> wrapMultiline(@NonNull String input,
                                             final int maxLineLength) {
        input = ChatColor.translateAlternateColorCodes('&', input);
        String[] paras = input.split("\n\n");
        List<String> result = new ArrayList<>();
        for (String para : paras) {
            // Find color prefix, if any
            int offset = 0;
            for (int i = 0; i < para.length() - 1; i += 1) {
                if (para.charAt(i) != ChatColor.COLOR_CHAR) continue;
                if (null == ChatColor.getByChar(para.charAt(i + 1))) continue;
                offset += 2;
            }
            String prefix = para.substring(0, offset);
            para = para.substring(offset);
            List<String> lines = wrap(para, maxLineLength).stream()
                .map(line -> prefix + line)
                .collect(Collectors.toList());
            if (!result.isEmpty()) result.add("");
            result.addAll(lines);
        }
        return result;
    }
}
