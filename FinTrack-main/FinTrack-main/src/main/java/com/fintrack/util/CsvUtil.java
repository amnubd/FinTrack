package com.fintrack.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for RFC 4180-compliant CSV reading, writing, and CSV formula injection mitigation (CWE-1236).
 */
public final class CsvUtil {

    private CsvUtil() {}

    /**
     * Escapes a field value for CSV export, quoting quotes and prefixing formula trigger characters.
     *
     * @param input raw string field
     * @return sanitized, CSV-safe escaped string
     */
    public static String escapeField(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = input.replace("\"", "\"\"");
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                sanitized = "'" + sanitized;
            }
        }
        return sanitized;
    }

    /**
     * Unescapes a parsed CSV field token, restoring formula prefixes and unescaping quotes.
     *
     * @param token raw token from parser
     * @return original unescaped string
     */
    public static String unescapeField(String token) {
        if (token == null) {
            return "";
        }
        String result = token;
        if (result.length() >= 2 && result.charAt(0) == '\'') {
            char second = result.charAt(1);
            if (second == '=' || second == '+' || second == '-' || second == '@' || second == '\t' || second == '\r') {
                result = result.substring(1);
            }
        }
        return result;
    }

    /**
     * Parses an entire CSV stream into records, properly handling multiline quoted fields.
     *
     * @param reader reader providing CSV character stream
     * @return list of parsed records (each record is an array of column tokens)
     * @throws IOException on I/O error
     */
    public static List<String[]> parseAll(Reader reader) throws IOException {
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
        List<String[]> records = new ArrayList<>();
        String line;
        StringBuilder recordBuilder = new StringBuilder();
        boolean inQuotes = false;

        while ((line = br.readLine()) != null) {
            if (recordBuilder.length() > 0) {
                recordBuilder.append("\n");
            }
            recordBuilder.append(line);

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\"') {
                    if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                        i++; // skip escaped quote
                    } else {
                        inQuotes = !inQuotes;
                    }
                }
            }

            if (!inQuotes) {
                String completeRecord = recordBuilder.toString();
                recordBuilder.setLength(0);
                if (!completeRecord.isBlank()) {
                    records.add(parseRecord(completeRecord));
                }
            }
        }
        if (recordBuilder.length() > 0) {
            String completeRecord = recordBuilder.toString();
            if (!completeRecord.isBlank()) {
                records.add(parseRecord(completeRecord));
            }
        }
        return records;
    }

    /**
     * Parses a single complete (possibly multiline) CSV record into its constituent column tokens.
     *
     * @param record complete record text
     * @return array of unescaped column tokens
     */
    public static String[] parseRecord(String record) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldWasQuoted = false;

        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (c == '\"') {
                fieldWasQuoted = true;
                if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                String token = fieldWasQuoted ? sb.toString() : sb.toString().trim();
                tokens.add(unescapeField(token));
                sb.setLength(0);
                fieldWasQuoted = false;
            } else {
                if (!inQuotes && fieldWasQuoted && Character.isWhitespace(c)) {
                    continue; // Skip whitespace between closing quote and next comma
                }
                sb.append(c);
            }
        }
        String token = fieldWasQuoted ? sb.toString() : sb.toString().trim();
        tokens.add(unescapeField(token));
        return tokens.toArray(new String[0]);
    }
}
