package com.winlator.core;

import java.util.ArrayDeque;

/** Keep the newest diagnostic lines without unbounded memory or disk usage. */
public final class StartupLog {
    private final int capacity;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private int length;
    private boolean truncated;

    public StartupLog(int capacity) {
        if (capacity < 64) throw new IllegalArgumentException("Log capacity is too small");
        this.capacity = capacity;
    }

    public synchronized void append(String text) {
        String line = text.replace('\0', '?');
        if (line.length() >= capacity) line = line.substring(line.length() - capacity + 1);
        line += "\n";
        while (length + line.length() > capacity && !lines.isEmpty()) {
            length -= lines.removeFirst().length();
            truncated = true;
        }
        lines.addLast(line);
        length += line.length();
    }

    public synchronized String snapshot() {
        StringBuilder result = new StringBuilder();
        if (truncated) result.append("[Earlier output omitted]\n");
        for (String line : lines) result.append(line);
        return result.toString();
    }
}
