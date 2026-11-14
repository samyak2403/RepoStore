package com.samyak.gitcore.util

/**
 * Lightweight, allocation-conscious source scanning helpers for stripping comments
 * (while preserving URLs inside string literals) and extracting balanced `{ ... }` blocks.
 */
internal object SourceCommentStripper {

    private val XML_COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")

    fun stripXmlComments(input: String): String {
        return XML_COMMENT_REGEX.replace(input, "")
    }

    /**
     * Strips `//` line comments and `/* ... */` block comments without touching string literals
     * such as `"https://example.com"` or `'foo // bar'`.
     */
    fun stripCStyleComments(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]

            // Triple-quoted Kotlin string literal
            if (c == '"' && i + 2 < n && input[i + 1] == '"' && input[i + 2] == '"') {
                out.append("\"\"\"")
                i += 3
                while (i < n) {
                    if (input[i] == '"' && i + 2 < n && input[i + 1] == '"' && input[i + 2] == '"') {
                        out.append("\"\"\"")
                        i += 3
                        break
                    }
                    out.append(input[i])
                    i++
                }
                continue
            }

            // Single- or double-quoted string literal
            if (c == '"' || c == '\'') {
                val quote = c
                out.append(quote)
                i++
                while (i < n) {
                    val sc = input[i]
                    out.append(sc)
                    if (sc == '\\' && i + 1 < n) {
                        i++
                        out.append(input[i])
                        i++
                        continue
                    }
                    i++
                    if (sc == quote || sc == '\n') {
                        break
                    }
                }
                continue
            }

            // Single-line comment //
            if (c == '/' && i + 1 < n && input[i + 1] == '/') {
                i += 2
                while (i < n && input[i] != '\n') {
                    i++
                }
                continue
            }

            // Block comment /* ... */
            if (c == '/' && i + 1 < n && input[i + 1] == '*') {
                i += 2
                while (i < n) {
                    if (input[i] == '*' && i + 1 < n && input[i + 1] == '/') {
                        i += 2
                        break
                    }
                    if (input[i] == '\n') {
                        out.append('\n')
                    }
                    i++
                }
                continue
            }

            out.append(c)
            i++
        }
        return out.toString()
    }

    /**
     * Finds `<keyword> { ... }` (ignoring braces inside quoted string literals) and returns
     * the inner body of that block, supporting arbitrary nesting depth.
     */
    fun extractBalancedBlock(commentStrippedCode: String, keyword: String): String? {
        val headerRegex = Regex("""\b${Regex.escape(keyword)}\s*\{""")
        val match = headerRegex.find(commentStrippedCode) ?: return null
        val openBraceIndex = match.range.last
        var depth = 1
        var i = openBraceIndex + 1
        val n = commentStrippedCode.length

        while (i < n && depth > 0) {
            val c = commentStrippedCode[i]
            if (c == '"' || c == '\'') {
                val quote = c
                i++
                while (i < n) {
                    val sc = commentStrippedCode[i]
                    if (sc == '\\' && i + 1 < n) {
                        i += 2
                        continue
                    }
                    i++
                    if (sc == quote) break
                }
                continue
            }
            if (c == '{') {
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0) {
                    return commentStrippedCode.substring(openBraceIndex + 1, i)
                }
            }
            i++
        }
        return null
    }
}
