/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msu.cme.rdp.classloader;

import edu.msu.cme.rdp.classloader.server.ClassServer;
import edu.msu.cme.rdp.classloader.utils.ClassNameMatcher;
import edu.msu.cme.rdp.classloader.utils.ClassNameMatcher.ClassNameMatches;
import edu.msu.cme.rdp.classloader.utils.ResourceURLStreamHandler;
import edu.msu.cme.rdp.classloader.utils.StreamUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author fishjord
 */
public class RDPClassLoader extends ClassLoader {

    private static final Logger log = Logger.getLogger(RDPClassLoader.class.getCanonicalName());
    private String classHost;
    private int port;
    private ResourceURLStreamHandler urlHandler = new ResourceURLStreamHandler();

    public RDPClassLoader() {
        super(RDPClassLoader.class.getClassLoader());
        init();
    }

    public RDPClassLoader(ClassLoader parent) {
        super(parent);
        init();
    }

    private void init() {
        classHost = System.getProperty("rdp.classloader.host", ClassServer.DEFAULT_HOST_NAME);
        String portStr = System.getProperty("rdp.classloader.port", ClassServer.DEFAULT_LISTEN_PORT + "");

        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid property rdp.classloader.port, must be a number", e);
        }
    }

    private Socket connect() {
        try {
            return new Socket(classHost, port);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unknown host: " + classHost, e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Okay, so I'm totally breaking the idea of class loading for java... Well,
     * not really, more like situating this class loader as the system class
     * loader for all classes loaded by RDPClassLoader...In my opinion this is
     * the expected behavior.
     *
     * Classes that start with java.* will NOT be loaded by this class loader.
     * Ever.
     *
     * @param name
     * @param resolve
     * @return
     */
    @Override
    protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class klass = findLoadedClass(name);

        if (klass == null) {
            try {
                klass = this.getParent().loadClass(name);
            } catch (ClassNotFoundException e) {
            }
        }

        if (klass == null) { // && !name.startsWith("java.")) {
            try {
                klass = findClass(name);
            } catch (ClassNotFoundException e) {
            }
        }

        if (klass == null) {
            throw new ClassNotFoundException(name);
        }

        if (resolve) {
            resolveClass(klass);
        }

        return klass;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Socket sock = connect();
        try {
            DataInputStream dis = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));

            dos.writeByte(ClassServer.CLASS_REQUEST);
            dos.writeUTF(name);
            dos.flush();

            int classSize = dis.readInt();
            if (classSize == -1) {
                throw new ClassNotFoundException();
            }

            byte[] code = new byte[classSize];
            StreamUtils.fillBuffer(code, dis);

            //log.log(Level.FINEST, "Retrieving class {0} (size= {1}) from class server", new Object[]{name, classSize});

            return defineClass(name.replace("/", "."), code, 0, code.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                sock.close();
            } catch (Exception e) {
            } //Don't care
        }
    }

    @Override
    protected URL findResource(String name) {
        URL ret = this.getParent().getResource(name);
        if (ret == null) {
            Socket sock = connect();
            try {
                DataInputStream dis = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));

                dos.writeByte(ClassServer.RESOURCE_SIZE_REQUEST);
                dos.writeUTF(name);
                dos.flush();

                int resourceSize = dis.readInt();

                if (resourceSize == -1) {
                    return null;
                } else {
                    log.log(Level.FINEST, "Retrieving resource {0} (size= {1}) from class server", new Object[]{name, resourceSize});

                    ret = new URL("rdpcl", classHost, port, (name.startsWith("/") ? name : "/" + name), urlHandler);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return ret;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        log.log(Level.WARNING, "Enumeration<URL> not override by RDPClassLoader, punting on {0}", name);
        return super.getResources(name);
    }

    private Set<String> getKnownMainClasses() {
        Socket sock = connect();
        try {
            DataOutputStream dos = new DataOutputStream(sock.getOutputStream());

            dos.writeByte(ClassServer.LIST_MAIN_CLASSES);
            dos.flush();

            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(sock.getInputStream()));
            Set<String> knownClasses = (Set<String>) ois.readObject();

            log.log(Level.FINEST, "Retrieving known classes (count= {0}) from class server", knownClasses.size());

            return knownClasses;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                sock.close();
            } catch (Exception e) {
            } //Don't care
        }
    }

    public static void main(String[] args) throws Throwable {

        if (args.length == 0) {
            System.err.println("No main class specified");
            System.exit(1);
        }

        String mainClass = args[0];
        String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
        Class klass = null;

        if (!(ClassLoader.getSystemClassLoader() instanceof RDPClassLoader)) {
            System.err.println("RDPClassLoader not set as system class loader (See property java.system.class.loader");
            System.exit(1);
        }

        RDPClassLoader loader = (RDPClassLoader) ClassLoader.getSystemClassLoader();

        try {
            klass = loader.findClass(mainClass);
        } catch (ClassNotFoundException e) {
            ClassNameMatches matches = ClassNameMatcher.findClassNameMatches(mainClass, loader.getKnownMainClasses(), 5);

            System.err.println("Couldn't find main class \"" + mainClass + "\"");
            if (mainClass.contains(".") || mainClass.contains("/")) {
                System.err.println("Best canonical matches:");

                for (String guess : matches.getCanonicalMatches()) {
                    System.err.println("\t" + guess);
                }
            } else {
                if (matches.getExactMatches().size() == 1) {
                    log.log(Level.INFO, "Found a single exact name match {0}", matches.getExactMatches());
                    klass = loader.findClass(matches.getExactMatches().get(0));
                } else {
                    System.err.println("Best name matches:");

                    for (String guess : matches.getNameMatches()) {
                        System.err.println("\t" + guess);
                    }
                }
            }

            if (klass == null) {
                System.exit(1);
            }
        }

        Method main = klass.getDeclaredMethod("main", newArgs.getClass());

        try {
            main.invoke(null, (Object) newArgs);
        } catch (InvocationTargetException e) {
            if (e.getCause() != null) {
                throw e.getCause();
            }
        }
    }
}
