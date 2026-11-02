package com.sdrtouch.core.exceptions;public class SdrException extends Exception {public static final int EXIT_UNKNOWN=1;public SdrException(int id){super("SDR error "+id);}}
