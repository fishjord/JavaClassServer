/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.msu.cme.rdp.classloader.codestore;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author fishjord
 */
public interface Resource {

    public InputStream getResourceStream() throws IOException;

    public long getLength();
}
