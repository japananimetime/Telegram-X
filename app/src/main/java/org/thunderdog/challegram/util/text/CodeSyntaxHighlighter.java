/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight, language-agnostic syntax highlighter for code blocks. This is a HEURISTIC tokenizer —
 * not a full per-language parser. It recognizes comments, string/char literals, numbers and a broad
 * union of keywords across the common curly-brace + scripting languages. Comment styles that are
 * ambiguous ({@code #}, {@code --}) are only enabled for languages that actually use them, so e.g.
 * a C {@code #include} preprocessor line is not mistaken for a comment.
 *
 * <p>Colors follow a VS Code Dark+/Light+ palette chosen by the luminance of the code-box background,
 * so the highlighting stays legible on every theme. The palette is resolved lazily on each draw via
 * {@link TextColorSet}, so it tracks theme changes without rebuilding the entities.</p>
 */
public final class CodeSyntaxHighlighter {
  private CodeSyntaxHighlighter () { }

  private static boolean isDarkBackground () {
    int c = Theme.getColor(ColorId.iv_preBlockBackground);
    double lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
    return lum < 0.5;
  }

  private static final TextColorSet KEYWORD = () -> isDarkBackground() ? 0xFF569CD6 : 0xFF0000FF;
  private static final TextColorSet STRING  = () -> isDarkBackground() ? 0xFFCE9178 : 0xFFA31515;
  private static final TextColorSet COMMENT = () -> isDarkBackground() ? 0xFF6A9955 : 0xFF008000;
  private static final TextColorSet NUMBER  = () -> isDarkBackground() ? 0xFFB5CEA8 : 0xFF098658;

  private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
    // control flow (shared by most languages)
    "if", "else", "elif", "for", "while", "do", "switch", "case", "default", "break",
    "continue", "return", "goto", "yield", "await", "async", "throw", "throws", "try",
    "catch", "finally", "except", "raise", "with", "match", "when", "where", "in", "of",
    "as", "is", "not", "and", "or", "begin", "end", "then", "loop", "unless", "until",
    // declarations & types
    "class", "interface", "enum", "struct", "trait", "impl", "object", "record", "module",
    "namespace", "package", "import", "from", "include", "using", "extends", "implements",
    "abstract", "final", "sealed", "open", "override", "virtual", "static", "const",
    "constexpr", "let", "var", "val", "def", "fun", "func", "function", "fn", "lambda",
    "public", "private", "protected", "internal", "friend", "inline", "extern", "export",
    "typedef", "type", "typename", "template", "operator", "this", "self", "super", "new",
    "delete", "void", "int", "long", "short", "char", "float", "double", "bool", "boolean",
    "byte", "string", "str", "list", "dict", "map", "set", "vector", "auto", "unsigned",
    "signed", "size_t", "true", "false", "null", "nil", "none", "undefined", "nullptr",
    "volatile", "mutable", "synchronized", "transient", "native", "throwable", "instanceof",
    // modern / misc keywords
    "defer", "select", "chan", "go", "range", "pub", "use", "mod", "crate", "ref", "dyn",
    "move", "unsafe", "macro", "echo", "print", "println", "elseif", "endif", "endfor",
    "endwhile", "global", "nonlocal", "pass", "del", "lambda", "require", "module",
    // SQL
    "select", "insert", "update", "delete", "create", "alter", "drop", "table", "join",
    "left", "right", "inner", "outer", "group", "order", "having", "limit", "values"
  ));

  public static @Nullable TextEntity[] highlight (@NonNull String code, @Nullable String language) {
    if (code.isEmpty()) {
      return null;
    }
    String lang = language != null ? language.trim().toLowerCase() : "";
    boolean hashComment = isHashCommentLanguage(lang);
    boolean dashComment = isDashCommentLanguage(lang);

    List<TextEntity> out = new ArrayList<>();
    final int n = code.length();
    int i = 0;
    while (i < n) {
      char c = code.charAt(i);
      // Line comment: //
      if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
        int start = i;
        i += 2;
        while (i < n && code.charAt(i) != '\n') {
          i++;
        }
        add(out, code, start, i, COMMENT);
      } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') { // Block comment: /* ... */
        int start = i;
        i += 2;
        while (i < n && !(code.charAt(i) == '*' && i + 1 < n && code.charAt(i + 1) == '/')) {
          i++;
        }
        i = Math.min(n, i + 2);
        add(out, code, start, i, COMMENT);
      } else if (hashComment && c == '#') {
        int start = i;
        while (i < n && code.charAt(i) != '\n') {
          i++;
        }
        add(out, code, start, i, COMMENT);
      } else if (dashComment && c == '-' && i + 1 < n && code.charAt(i + 1) == '-') {
        int start = i;
        while (i < n && code.charAt(i) != '\n') {
          i++;
        }
        add(out, code, start, i, COMMENT);
      } else if (c == '"' || c == '\'' || c == '`') { // String / char literal
        char quote = c;
        int start = i;
        i++;
        while (i < n) {
          char d = code.charAt(i);
          if (d == '\\' && i + 1 < n) {
            i += 2;
            continue;
          }
          i++;
          if (d == quote || d == '\n') {
            break;
          }
        }
        add(out, code, start, i, STRING);
      } else if (isDigit(c) || (c == '.' && i + 1 < n && isDigit(code.charAt(i + 1)))) { // Number
        int start = i;
        i++;
        while (i < n && isNumberPart(code.charAt(i))) {
          i++;
        }
        add(out, code, start, i, NUMBER);
      } else if (isIdentifierStart(c)) { // Identifier / keyword
        int start = i;
        i++;
        while (i < n && isIdentifierPart(code.charAt(i))) {
          i++;
        }
        if (KEYWORDS.contains(code.substring(start, i))) {
          add(out, code, start, i, KEYWORD);
        }
      } else {
        i++;
      }
    }
    return out.isEmpty() ? null : out.toArray(new TextEntity[0]);
  }

  private static void add (List<TextEntity> out, String code, int start, int end, TextColorSet colorSet) {
    if (end > start) {
      out.add(new TextEntityCustom(null, null, code, start, end, 0, null).setCustomColorSet(colorSet));
    }
  }

  private static boolean isDigit (char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isNumberPart (char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') ||
      c == '.' || c == 'x' || c == 'X' || c == 'b' || c == 'o' || c == '_' || c == 'e' ||
      c == 'E' || c == 'l' || c == 'L' || c == 'f' || c == 'F' || c == 'u' || c == 'U';
  }

  private static boolean isIdentifierStart (char c) {
    return Character.isLetter(c) || c == '_' || c == '$';
  }

  private static boolean isIdentifierPart (char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  private static boolean isHashCommentLanguage (String lang) {
    switch (lang) {
      case "python": case "py": case "ruby": case "rb": case "bash": case "sh": case "shell":
      case "zsh": case "yaml": case "yml": case "toml": case "perl": case "pl": case "r":
      case "makefile": case "make": case "dockerfile": case "ini": case "conf": case "cfg":
      case "properties": case "elixir": case "ex": case "crystal": case "nim": case "php":
      case "powershell": case "ps1": case "awk": case "tcl":
        return true;
      default:
        return false;
    }
  }

  private static boolean isDashCommentLanguage (String lang) {
    switch (lang) {
      case "sql": case "lua": case "haskell": case "hs": case "ada": case "elm": case "vhdl":
      case "applescript":
        return true;
      default:
        return false;
    }
  }
}
