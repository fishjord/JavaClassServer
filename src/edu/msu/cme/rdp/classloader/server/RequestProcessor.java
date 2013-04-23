/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msu.cme.rdp.classloader.server;

import edu.msu.cme.rdp.classloader.codestore.ClassCodeStore;
import edu.msu.cme.rdp.classloader.codestore.Resource;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author fishjord
 */
public class RequestProcessor implements Runnable {

    private static final Logger logger = Logger.getLogger(RequestProcessor.class.getCanonicalName());
    private Socket clientSock;
    private ClassCodeStore codeStore;

    public RequestProcessor(ClassCodeStore codeStore, Socket clientSock) throws IOException {
        this.codeStore = codeStore;
        this.clientSock = clientSock;
    }

    private void joinStreams(InputStream is, OutputStream os) throws IOException {
        int read = 0;
        byte[] buf = new byte[1024];

        while ((read = is.read(buf)) != -1) {
            os.write(buf, 0, read);
        }
    }

    public void run() {
        String clientName = clientSock.toString();

        try {
            DataInputStream dis = new DataInputStream(new BufferedInputStream(clientSock.getInputStream()));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(clientSock.getOutputStream()));

            byte cmd = dis.readByte();

            if (cmd == ClassServer.CLASS_REQUEST) {
                String className = dis.readUTF().replace(".", "/");
                logger.log(Level.FINEST, "Recieved request for class {0} from client {1}", new Object[]{className, clientName});

                byte[] code = codeStore.getRegisteredClass(className);
                if (code == null) {
                    dos.writeInt(-1);
                } else {
                    dos.writeInt(code.length);
                    dos.write(code);
                }
            } else if (cmd == ClassServer.RESOURCE_REQUEST) {
                String resourceName = dis.readUTF();
                //XXX: Dirty fucking hack...gotta figure out a better way to do this
                //However that involves a much deeper understanding of how the ClassLoader.getResource() method is suppose to resolve stuff
                if (!resourceName.startsWith("/")) {
                    resourceName = "/" + resourceName;
                }

                Resource resource = codeStore.getRegisteredResource(resourceName);

                logger.log(Level.FINEST, "Recieved request for resource {0} from client {1})", new Object[]{resourceName, clientName});

                if (resource != null) {
                    joinStreams(resource.getResourceStream(), dos);
                }
            } else if (cmd == ClassServer.RESOURCE_SIZE_REQUEST) {
                String resourceName = dis.readUTF();
                //XXX: Dirty fucking hack...gotta figure out a better way to do this
                //However that involves a much deeper understanding of how the ClassLoader.getResource() method is suppose to resolve stuff
                if (!resourceName.startsWith("/")) {
                    resourceName = "/" + resourceName;
                }

                Resource resource = codeStore.getRegisteredResource(resourceName);

                logger.log(Level.FINEST, "Recieved request for resource length {0} from client {1})", new Object[]{resourceName, clientName});

                if (resource == null) {
                    dos.writeInt(-1);
                } else {
                    //Yes yes, it's an int, and as such will only support resources up to 2g in size
                    dos.writeInt((int) resource.getLength());
                }
            } else if (cmd == ClassServer.LIST_CLASSES) {
                ObjectOutputStream oos = new ObjectOutputStream(dos);
                Set<String> knownClasses = codeStore.getKnownClasses();
                logger.log(Level.FINEST, "Recieved request to list known classes (count={0}) from client {1})", new Object[]{knownClasses.size(), clientName});
                oos.writeObject(knownClasses);
                oos.close();
            } else if (cmd == ClassServer.LIST_MAIN_CLASSES) {
                ObjectOutputStream oos = new ObjectOutputStream(dos);
                Set<String> knownClasses = codeStore.getKnownMainClasses();
                logger.log(Level.FINEST, "Recieved request to list known classes (count={0}) from client {1})", new Object[]{knownClasses.size(), clientName});
                oos.writeObject(knownClasses);
                oos.close();
            } else {
                logger.log(Level.SEVERE, "Unknown request type {0}", cmd);
            }

            dos.flush();

        } catch (EOFException e) {
            logger.log(Level.SEVERE, "Client unexpected closed: " + clientName, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Client unexpected closed: " + clientName, e);
        } finally {
            try {
                clientSock.close();
            } catch (Exception e) {
            }
        }
    }
}
