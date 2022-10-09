/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2022 by Signalware Ltd <driver@sdrtouch.com>
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sdrtouch.tools;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ArgumentParser {
    private final Map<String, String> argumentsMap;

    private static String unquote(String string) {
        int length = string.length();
        if (length < 2) return  string;

        char firstChar = string.charAt(0);
        char lastChar = string.charAt(length-1);

        if ((firstChar == '"' && lastChar == '"') || (firstChar == '\'' && lastChar == '\'')) {
            return string.substring(1,length-1);
        }

        return string;
    }

    private static List<String> splitIgnoringQuotes(String rawArguments) {
        List<String> result = new LinkedList<>();
        char currentqoute = 0;
        char[] input = rawArguments.toCharArray();
        StringBuilder resultingString = new StringBuilder();

        for (char current_char : input) {
            boolean use_current_char = false;

            switch (current_char) {
                case '\'':
                case '"':
                    if (currentqoute == 0) {
                        currentqoute = current_char;
                    } else if (currentqoute == current_char) {
                        currentqoute = 0;
                    } else {
                        use_current_char = true;
                    }
                    break;
                case ' ':
                    if (currentqoute == 0) {
                        if (resultingString.length() != 0) result.add(resultingString.toString());
                        resultingString.setLength(0);
                    } else {
                        use_current_char = true;
                    }
                    break;
                default:
                    use_current_char = true;
            }

            if (use_current_char) resultingString.append(current_char);
        }

        if (resultingString.length() != 0) result.add(resultingString.toString());
        return result;
    }

    private static Map<String, String> toMap(String rawArguments) throws IllegalArgumentException {
        Map<String, String> argumentsMap = new HashMap<>();

        List<String> parsedArg = splitIgnoringQuotes(rawArguments);
        for (int i = 0; i < parsedArg.size(); i+=2) {
            String argument = parsedArg.get(i).trim();
            if (i == parsedArg.size() - 1) throw  new IllegalArgumentException("No value for argument "+argument);
            if (!argument.startsWith("-")) throw new IllegalArgumentException("Argument "+argument+" must start with a dash!");
            if (argument.length() < 2) throw new IllegalArgumentException("Missing argument after dash");
            String value = unquote(parsedArg.get(i + 1).trim());
            argumentsMap.put(argument.substring(1), value);
        }

        return argumentsMap;
    }

    public ArgumentParser(String rawArguments) throws IllegalArgumentException {
        argumentsMap = toMap(rawArguments);
    }

    private String getStringArgumentRaw(String argument) {
        if (argument == null || argument.length() < 1) throw new IllegalArgumentException("Argument must be at least one character long!");
        if (!argument.trim().equals(argument)) throw new IllegalArgumentException("Arguments cannot have trailing or leading spaces!");
        return argumentsMap.get(argument);
    }

    public String getStringArgument(String argument) {
        String value = getStringArgumentRaw(argument);
        if (value == null) throw new IllegalArgumentException("Expected string argument '"+argument+"'");
        return value;
    }

    public String getStringArgumentOrDefault(String argument, String defValue) {
        String value = getStringArgumentRaw(argument);
        return  value == null ? defValue : value;
    }

    public int getIntArgument(String argument) {
        String value = getStringArgumentRaw(argument);
        if (value == null) throw new IllegalArgumentException("Expected integer argument '"+argument+"'");
        return Integer.parseInt(value);
    }

    public int getIntArgumentOrDefault(String argument, int defValue) {
        String value = getStringArgumentRaw(argument);
        return value == null ? defValue : Integer.parseInt(value);
    }

    public long getLongArgumentOrDefault(String argument, long defValue) {
        String value = getStringArgumentRaw(argument);
        return value == null ? defValue : Long.parseLong(value);
    }

    public long getLongArgument(String argument) {
        String value = getStringArgumentRaw(argument);
        if (value == null) throw new IllegalArgumentException("Expected long integer argument '"+argument+"'");
        return Long.parseLong(value);
    }
}
