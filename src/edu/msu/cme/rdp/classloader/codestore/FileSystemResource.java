/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.msu.cme.rdp.classloader.codestore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author fishjord
 */
public class FileSystemResource implements Resource {
    private File resourceFile;

    public FileSystemResource(File resourceFile) {
        this.resourceFile = resourceFile;
    }

    public InputStream getResourceStream() throws IOException {
        return new FileInputStream(resourceFile);
    }

    public long getLength() {
        return resourceFile.length();
    }
}
