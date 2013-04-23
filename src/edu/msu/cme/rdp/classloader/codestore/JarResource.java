/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.msu.cme.rdp.classloader.codestore;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 *
 * @author fishjord
 */
public class JarResource implements Resource {

    private JarFile jarFile;
    private JarEntry jarEntry;

    public JarResource(JarFile jarFile, JarEntry jarEntry) {
        this.jarFile = jarFile;
        this.jarEntry = jarEntry;
    }

    public InputStream getResourceStream() throws IOException {
        return jarFile.getInputStream(jarEntry);
    }

    public long getLength() {
        return jarEntry.getSize();
    }
}
