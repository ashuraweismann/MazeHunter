package com.mazehunter.server.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;

/** Reads newline-delimited text while limiting the UTF-8 byte length of each line. */
final class LimitedUtf8LineReader {

    static final class LineTooLongException extends IOException {
        LineTooLongException(int maximumBytes) {
            super("Protocol line exceeds " + maximumBytes + " UTF-8 bytes");
        }
    }

    private final BufferedReader reader;
    private final int maximumBytes;

    LimitedUtf8LineReader(BufferedReader reader, int maximumBytes) {
        this.reader = Objects.requireNonNull(reader, "reader");
        if (maximumBytes < 1) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    /**
     * Reads one LF-delimited line. A CR immediately before LF is treated as part of
     * the line ending, matching BufferedReader.readLine() for CRLF input.
     */
    String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int byteCount = 0;
        boolean pendingCarriageReturn = false;
        boolean sawContent = false;

        while (true) {
            int value = reader.read();
            if (value == -1) {
                if (pendingCarriageReturn) {
                    byteCount = appendSingleByte(line, byteCount, '\r');
                    sawContent = true;
                }
                return sawContent ? line.toString() : null;
            }

            char character = (char) value;
            if (character == '\n') {
                return line.toString();
            }

            if (pendingCarriageReturn) {
                byteCount = appendSingleByte(line, byteCount, '\r');
                pendingCarriageReturn = false;
                sawContent = true;
            }

            if (character == '\r') {
                pendingCarriageReturn = true;
                continue;
            }

            if (Character.isHighSurrogate(character)) {
                int followingValue = reader.read();
                if (followingValue == -1 || !Character.isLowSurrogate((char) followingValue)) {
                    throw new IOException("Invalid UTF-8 character sequence in protocol line");
                }
                if (byteCount + 4 > maximumBytes) {
                    throw new LineTooLongException(maximumBytes);
                }
                line.append(character).append((char) followingValue);
                byteCount += 4;
                sawContent = true;
                continue;
            }

            if (Character.isLowSurrogate(character)) {
                throw new IOException("Invalid UTF-8 character sequence in protocol line");
            }

            int encodedLength = utf8Length(character);
            if (byteCount + encodedLength > maximumBytes) {
                throw new LineTooLongException(maximumBytes);
            }
            line.append(character);
            byteCount += encodedLength;
            sawContent = true;
        }
    }

    private int appendSingleByte(StringBuilder line, int currentByteCount, char character)
            throws LineTooLongException {
        if (currentByteCount + 1 > maximumBytes) {
            throw new LineTooLongException(maximumBytes);
        }
        line.append(character);
        return currentByteCount + 1;
    }

    private static int utf8Length(char character) {
        if (character <= 0x7f) {
            return 1;
        }
        if (character <= 0x7ff) {
            return 2;
        }
        return 3;
    }
}
