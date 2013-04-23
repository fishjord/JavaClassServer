/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msu.cme.rdp.classloader.utils;

import edu.msu.cme.rdp.classloader.server.ClassServer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 *
 * @author fishjord
 */
public class ResourceURLStreamHandler extends URLStreamHandler {

    public static class ResourceURLConnection extends URLConnection {
        private InputStream is;

        public ResourceURLConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() throws IOException {
            if (!connected) {
                Socket s = new Socket(url.getHost(), url.getPort());

                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                dos.writeByte(ClassServer.RESOURCE_REQUEST);
                dos.writeUTF(url.getPath());
                dos.flush();
                s.shutdownOutput();

                is = new BufferedInputStream(s.getInputStream());

                connected = true;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            return is;
        }

        @Override
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new ResourceURLConnection(u);
    }
}

