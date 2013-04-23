/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.msu.cme.rdp.classloader.server;

import edu.msu.cme.rdp.classloader.codestore.ClassCodeStore;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

/**
 *
 * @author fishjord
 */
public class ClassServer {
    public static final int DEFAULT_LISTEN_PORT = 12345;
    public static final String DEFAULT_HOST_NAME = "nonpareil.rdp";

    public static final byte CLASS_REQUEST = 1;
    public static final byte RESOURCE_REQUEST = 2;
    public static final byte RESOURCE_SIZE_REQUEST = 3;
    public static final byte LIST_CLASSES = 4;
    public static final byte LIST_MAIN_CLASSES = 5;
    
    private static final Logger logger = Logger.getLogger(ClassServer.class.getCanonicalName());
    
    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("c", "max-connections", true, "Maximum number of connections (default=15)");
        options.addOption("p", "listen-port", true, "Set the port to listen for connections on (default=12345)");

        int maxConnections = 5;
        int listenPort = DEFAULT_LISTEN_PORT;

        ClassCodeStore classStore = new ClassCodeStore();
        
        try {
            CommandLine line = new PosixParser().parse(options, args);

            if(line.hasOption("max-connections")) {
                maxConnections = Integer.parseInt(line.getOptionValue("max-connections"));
            }

            if(line.hasOption("listen-port")) {
                listenPort = Integer.parseInt(line.getOptionValue("listen-port"));
            }

            String[] paths = line.getArgs();

            if(paths.length < 1) {
                throw new Exception("Must provide one or more paths to load classes from");
            }

            for(String path : paths) {
                classStore.registerDirectory(new File(path));
            }

        } catch(Exception e) {
            new HelpFormatter().printHelp("ClassLoaderServer [options] <class_directory> ...", options);
            System.out.println("Error: " + e.getMessage());
            logger.log(Level.SEVERE, "Failed to start class server", e);
            System.exit(1);
        }

        Executor threadPool = Executors.newFixedThreadPool(maxConnections);
        ServerSocket serverSock = new ServerSocket(listenPort);

        logger.log(Level.INFO, "Registered {0} classes, {1} have main methods", new Object[]{classStore.getKnownClasses().size(), classStore.getKnownMainClasses().size()});
        logger.log(Level.INFO, "Server started up listening on {0}", serverSock.getLocalSocketAddress());

        while(true) {
            Socket clientSock = serverSock.accept();
            logger.log(Level.INFO, "Connection recieved from client {0}", new Object[] { clientSock });

            threadPool.execute(new RequestProcessor(classStore, clientSock));
        }
    }

}
