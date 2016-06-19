/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2016 by Martin Marinov <martintzvetomirov@gmail.com>
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

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ArgumentParserTest {

    @Test
    public void testBasicParsing() {
        ArgumentParser argumentParser = new ArgumentParser("-a 123 -bc hello");
        assertThat(argumentParser.getIntArgumentOrDefault("a", -1), equalTo(123));
        assertThat(argumentParser.getStringArgument("bc"), equalTo("hello"));
    }

    @Test
    public void testBasicParsingExtraSpace() {
        ArgumentParser argumentParser = new ArgumentParser("-a 123      -bc   hello");
        assertThat(argumentParser.getStringArgument("a"), equalTo("123"));
        assertThat(argumentParser.getStringArgument("bc"), equalTo("hello"));
    }

    @Test
    public void testDoubleQuote() {
        ArgumentParser argumentParser = new ArgumentParser("-bc \"hello\"");
        assertThat(argumentParser.getStringArgument("bc"), equalTo("hello"));
    }

    @Test
    public void testDoubleQuoteSpace() {
        ArgumentParser argumentParser = new ArgumentParser("-bc \"hello world\"");
        assertThat(argumentParser.getStringArgument("bc"), equalTo("hello world"));
    }

    @Test
    public void testSingleQuoteSpace() {
        ArgumentParser argumentParser = new ArgumentParser("-bc 'hello world'");
        assertThat(argumentParser.getStringArgument("bc"), equalTo("hello world"));
    }

    @Test
    public void testMixedQuoteSpace() {
        ArgumentParser argumentParser = new ArgumentParser("-bc 'hello \"world\"'");
        assertThat(argumentParser.getStringArgument("bc"), equalTo("hello \"world\""));
    }

    @Test
    public void setDefaultIntegerIfArgMissing() {
        ArgumentParser argumentParser = new ArgumentParser("-bc 'hello \"world\"'");
        assertThat(argumentParser.getIntArgumentOrDefault("a", -1), equalTo(-1));
    }

    @Test
    public void testNoArgs() {
        new ArgumentParser("");
    }

    @Test
    public void testSpace() {
        new ArgumentParser(" ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBrokenArgs1() {
        new ArgumentParser("-bc Hello World");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBrokenArgs2() {
        new ArgumentParser("-bc Hello -dc ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBrokenArgs3() {
        new ArgumentParser("-bc Hello - world");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadEmptyArg() {
        ArgumentParser argumentParser = new ArgumentParser("-a 123 -bc hello");
        argumentParser.getStringArgument("");
    }

    @Test(expected = NumberFormatException.class)
    public void testNumericExceptionWhenNumberIsWrong() {
        ArgumentParser argumentParser = new ArgumentParser("-a 123abc");
        argumentParser.getIntArgumentOrDefault("a", -1);
    }
}