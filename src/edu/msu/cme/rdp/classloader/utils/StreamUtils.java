/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msu.cme.rdp.classloader.utils;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author fishjord
 */
public class StreamUtils {

    public static void fillBuffer(byte[] buffer, InputStream is) throws IOException {
        int offset = 0;
        int len = buffer.length;

        while (len > 0) {
            int read = is.read(buffer, offset, len);

            offset += read;
            len -= read;
        }
    }
}
