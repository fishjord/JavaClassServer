/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.msu.cme.rdp.classloader.codestore;

import edu.msu.cme.rdp.classloader.utils.StreamUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

/**
 *
 * @author fishjord
 */
public class ClassCodeStore {
    private final static String propsPath = "/props/ClassCodeStore.properties";

    private Map<String, byte[]> knownClasses = new HashMap();
    private Set<String> mainClasses = new HashSet();
    private Map<String, Resource> resources = new HashMap();

    private final Set<String> classesRootDirs;
    private final Set<String> ignoreDirs;

    public ClassCodeStore() {
        try {
            InputStream is = this.getClass().getResourceAsStream(propsPath);
            if(is == null) {
                throw new IOException("Failed to find properties file " + propsPath);
            }

            Properties props = new Properties();
            props.load(is);

            Set<String> tmp = new HashSet(Arrays.asList(props.getProperty("codestore.class_dirs").trim().split(",")));
            classesRootDirs = Collections.unmodifiableSet(tmp);

            tmp = new HashSet(Arrays.asList(props.getProperty("codestore.ignore_dirs").trim().split(",")));
            ignoreDirs = Collections.unmodifiableSet(tmp);
        } catch(IOException e) {
            throw new RuntimeException("Failed to load ClassCodeStore properties", e);
        }
    }

    private static class MainMethodDetector {
        private static final Type strArr = Type.getType((new String[] {}).getClass());

        public boolean containsMain(String name, byte[] b) {
            try {
                JavaClass jc = new ClassParser(new ByteArrayInputStream(b), name).parse();
                for(Method m : jc.getMethods()) {
                    if(m.isStatic() && m.isPublic() &&
                            m.getName().equals("main") &&
                            m.getReturnType() == Type.VOID &&
                            m.getArgumentTypes().length == 1 &&
                            m.getArgumentTypes()[0].equals(strArr)) {
                        return true;
                    }
                }
            } catch(IOException e) {
                throw new RuntimeException(e);
            }

            return false;
        }
    }

    private static final MainMethodDetector mainMethodDetector = new MainMethodDetector();
    private static final Logger logger = Logger.getLogger(ClassCodeStore.class.getCanonicalName());

    private void registerClass(String classFile, InputStream is, String source) throws IOException {
        String className = classFile.replace(".class", "");
        byte[] classBytes = new byte[is.available()];

        StreamUtils.fillBuffer(classBytes, is);
        is.close();

        logger.log(Level.FINEST, "Registering class byte code for {0}  (already known={2})", new Object[]{className, knownClasses.containsKey(className)});

        knownClasses.put(className, classBytes);
        if(mainMethodDetector.containsMain(className, classBytes)) {
            mainClasses.add(className);
        }
    }

    private void registerResource(String resourceName, Resource resource) {
        logger.log(Level.FINEST, "Registering resource {0} length {1} (already known={2})", new Object[]{resourceName, resource.getLength(), resources.containsKey(resourceName)});
        resources.put(resourceName, resource);
    }

    private void registerJar(File jar) throws IOException {
        JarFile jarFile = new JarFile(jar);
        for(JarEntry jarEntry : Collections.list(jarFile.entries())) {
            if(jarEntry.getName().endsWith(".class")) {
                registerClass(jarEntry.getName(), jarFile.getInputStream(jarEntry), jar.getAbsolutePath());
            } else if(!jarEntry.isDirectory()) {
                registerResource("/" + jarEntry.getName(), new JarResource(jarFile, jarEntry));
            }
        }
    }

    private void registerDirectory(File directory, String classPath) throws IOException {

        if(directory == null) {
            logger.log(Level.SEVERE, "I got a null directory, classpath={0}", classPath);
            return;
        }
        File[] contents = directory.listFiles();
        if(contents == null) {
            logger.log(Level.SEVERE, "null directory contents, classpath={0}", classPath);
            return;
        }

        for(File f : contents) {
            if(f.isFile()) {
                if(f.getName().endsWith(".class")) {
                    registerClass(classPath + f.getName(), new FileInputStream(f), f.getAbsolutePath());
                } else if(f.getName().endsWith(".jar") && !f.getName().startsWith(".")) {   //God damn resource forks.
                    registerJar(f);
                } else {
                    registerResource("/" + classPath + f.getName(), new FileSystemResource(f));
                }
            } else if(f.isDirectory()) {
                registerDirectory(f, classPath + f.getName() + "/");
            }
        }
    }

    public void registerDirectory(File directory) throws IOException {
        if(!directory.isDirectory()) {
            throw new IllegalArgumentException("Directory must be a...directory");
        }

        for(File f : directory.listFiles()) {

            if(f.isFile()) {
                if(f.getName().endsWith(".jar")) {
                    logger.log(Level.INFO, "Loading classes from jar file {0} in directory {1}", new Object[] { f.getName(), f.getParent() });
                    registerJar(f);
                }
            } else if(f.isDirectory()) {
                if(classesRootDirs.contains(f.getName())) {
                    logger.log(Level.INFO, "Loading classes from directory {0}", f);
                    registerDirectory(f, "");
                } else if(!ignoreDirs.contains(f.getName())) {
                    registerDirectory(f);
                }
            }
        }
    }

    public byte[] getRegisteredClass(String className) {
        return knownClasses.get(className);
    }

    public Resource getRegisteredResource(String resourceName) {
        return resources.get(resourceName);
    }

    public Set<String> getKnownClasses() {
        return new HashSet(knownClasses.keySet());
    }

    public Set<String> getKnownMainClasses() {
        return Collections.unmodifiableSet(mainClasses);
    }
}
