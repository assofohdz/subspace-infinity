/*
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * A class loader that loads classes from directories and/or zip-format file
 * such as JAR file. It tracks the modification time of the classes it loads to
 * permit reloading through re-instantiation.
 * <P>
 * When the classloader reports its creator that one of the classes it has
 * loaded has changed on disk, it should discard the classloader and create a
 * new instance using <CODE>reinstantiate</CODE>. The classes are then reloaded
 * into the new classloader as required.
 *
 * <P>
 * The classloader can also load resources, which are a means for packaging
 * application data such as images within a jar file or directory.
 *
 * <P>
 * The classloader always first tries to load classes and resources from the
 * system, and uses it's own path if that fails. This is also done if an empty
 * repository is passed at construction.
 *
 * <P>
 * <B>How autoreload works:</B>
 * </P>
 *
 * <P>
 * The Java VM considers two classes the same if they have the same
 * fully-qualified name <B>and</B> if they were loaded from the same
 * <CODE>ClassLoader</CODE>.
 *
 * <P>
 * There is no way for a classloader to 'undefine' a class once it has been
 * loaded. However, the servlet engine can discard a classloader and the classes
 * it contains, causing the
 *
 * <P>
 * The <CODE>JServServletManager</CODE> creates a new instance of the
 * classloader each time it detects that any of the loaded classes have changed.
 *
 * <P>
 * Before terminating, all servlets are destroyed.
 *
 * <P>
 * According to the Java Language Specification (JLS), classes may be
 * garbage-collected when there are no longer any instances of that class and
 * the <CODE>java.lang.Class</CODE> object is finalizable. It is intended that
 * this be the case when a <CODE>JServClassLoader</CODE> is discarded.
 *
 * <P>
 * Many VM releases did not implement class garbage collection properly. In such
 * a VM, the memory usage will continue to grow if autoreloading is enable.
 * Running the VM with <CODE>-verbosegc</CODE> (or the corresponding option for
 * non-Javasoft VMs) may give some debugging information.
 *
 * <P>
 * It is important that the <CODE>destroy</CODE> method be implemented properly,
 * as servlets may be destroyed and reinitialized several times in the life of a
 * VM.
 *
 * @author Francis J. Lacoste
 * @author Martin Pool
 * @author Jim Heintz
 * @author <a href="mailto:stefano@apache.org">Stefano Mazzocchi</a>
 * @version $Revision: 9187 $ $Date: 2016-01-22 00:50:59 +0100 (fr, 22 jan 2016)
 *          $
 * @see java.lang.ClassLoader
 */
public class AdaptiveClassLoader extends ClassLoader {

    /**
     * Generation counter, incremented for each classloader as they are created.
     */
    static private int generationCounter = 0;

    /**
     * Generation number of the classloader, used to distinguish between different
     * instances.
     */
    @SuppressWarnings("unused")
    private final int generation;

    /**
     * Cache of the loaded classes. This contains ClassCacheEntry keyed by class
     * names.
     */
    private final Hashtable<String, ClassCacheEntry> cache;

    /**
     * Save our class loader for chaining, and speed purposes.
     */
    private final ClassLoader myParentClassLoader;

    /**
     * The classpath which this classloader searches for class definitions. Each
     * element of the vector should be either a directory, a .zip file, or a .jar
     * file.
     * <p>
     * It may be empty when only system classes are controlled.
     */
    private final Vector<File> repository;

    /**
     * Private class used to maintain information about the classes that we loaded.
     */
    private static class ClassCacheEntry {

        /**
         * The actual loaded class
         */
        Class<?> loadedClass;

        /**
         * The file from which this class was loaded; or null if it was loaded from the
         * system.
         */
        File origin;

        /**
         * The time at which the class was loaded from the origin file, in ms since the
         * epoch.
         */
        long lastModified;

        /**
         * Check whether this class was loaded from the system.
         */
        public boolean isSystemClass() {
            return origin == null;
        }
    }

    // ------------------------------------------------------- Constructors
    /**
     * Creates a new class loader that will load classes from specified class
     * repositories.
     *
     * @param classRepository An set of File classes indicating directories and/or
     *                        zip/jar files. It may be empty when only system
     *                        classes are loaded.
     * @throws java.lang.IllegalArgumentException if the objects contained in the
     *                                            vector are not a file instance or
     *                                            the file is not a valid directory
     *                                            or a zip/jar file.
     */
    public AdaptiveClassLoader(final Vector<File> classRepository) throws IllegalArgumentException {
        this(classRepository, null);
    }

    /**
     * Creates a new class loader that will load classes from specified class
     * repositories.
     *
     * @param classRepository    An set of File classes indicating directories
     *                           and/or zip/jar files. It may be empty when only
     *                           system classes are loaded.
     * @param chainedClassLoader A class loader to attempt to load classes as
     *                           resources thru before falling back on the default
     *                           system loaders.
     * @throws java.lang.IllegalArgumentException if the objects contained in the
     *                                            vector are not a file instance or
     *                                            the file is not a valid directory
     *                                            or a zip/jar file.
     */
    public AdaptiveClassLoader(final Vector<File> classRepository, final ClassLoader chainedClassLoader)
            throws IllegalArgumentException {
        myParentClassLoader = chainedClassLoader;

        // Create the cache of loaded classes
        cache = new Hashtable<>();

        // Verify that all the repository are valid.
        final Enumeration<File> e = classRepository.elements();

        while (e.hasMoreElements()) {
            File file = e.nextElement();
            File[] files;

            files = SimpleFileFilter.fileOrFiles(file);

            if (files != null) {
                for (final File file2 : files) {
                    file = file2;

                    // Check to see if we have proper access.
                    if (!file.exists()) {
                        throw new IllegalArgumentException("Repository " + file.getAbsolutePath() + " doesn't exist!");
                    } else if (!file.canRead()) {
                        throw new IllegalArgumentException(
                                "Do not have read access for file " + file.getAbsolutePath());
                    }

                    // Check that it is a directory or zip/jar file
                    if ((!file.isDirectory() && !isZipOrJarArchive(file))) {
                        throw new IllegalArgumentException(
                                file.getAbsolutePath() + " is not a directory or zip/jar file"
                                        + " or if it's a zip/jar file then it is corrupted.");
                    }
                }
            }
        }

        // Store the class repository for use
        repository = classRepository;

        // Increment and store generation counter
        generation = generationCounter++;
    }

    // ------------------------------------------------------- Methods
    /**
     * Test if a file is a ZIP or JAR archive.
     *
     * @param file the file to be tested.
     * @return true if the file is a ZIP/JAR archive, false otherwise.
     */
    private boolean isZipOrJarArchive(final File file) {
        boolean result = false;
        try (ZipFile zipFile = new ZipFile(file)) {
            result = true;
        } catch (final ZipException e) {
            result = false;
        } catch (final IOException e) {
            result = false;
        }
        return result;
    }

    /**
     * Check to see if a given class should be reloaded because of a modification to
     * the original class.
     *
     * @param classname The name of the class to check for modification.
     * @return true if the class should be reloaded, false if not
     */
    public synchronized boolean shouldReload(final String classname) {

        final ClassCacheEntry entry = cache.get(classname);

        if (entry == null) {
            // class wasn't even loaded
            return false;
        } else if (entry.isSystemClass()) {
            // System classes cannot be reloaded
            return false;
        } else {
            final boolean reload = (entry.origin.lastModified() != entry.lastModified);
            return reload;
        }
    }

    /**
     * Check whether the classloader should be reinstantiated.
     * <P>
     * The classloader must be replaced if there is any class whose origin file has
     * changed since it was last loaded.
     *
     * @return true if the class loader should be reloaded, false otherwise
     */
    public synchronized boolean shouldReload() {

        // Check whether any class has changed
        final Enumeration<ClassCacheEntry> e = cache.elements();

        while (e.hasMoreElements()) {
            final ClassCacheEntry entry = e.nextElement();

            if (entry.isSystemClass()) {
                continue;
            }

            // XXX: Because we want the classloader to be an accurate
            // reflection of the contents of the repository, we also
            // reload if a class origin file is now missing. This
            // probably makes things a bit more fragile, but is OK in
            // a servlet development situation. <mbp@pharos.com.au>
            final long msOrigin = entry.origin.lastModified();

            if (msOrigin == 0) {
                // class no longer exists
                return true;
            }

            if (msOrigin != entry.lastModified) {
                // class is modified
                return true;
            }
        }

        // No changes, no need to reload
        return false;
    }

    /**
     * Re-instantiate this class loader.
     * <p>
     * This method creates a new instance of the class loader that will load classes
     * form the same path as this one.
     *
     * @return the new adaptive class loader instance
     */
    public AdaptiveClassLoader reinstantiate() {
        return new AdaptiveClassLoader(repository, myParentClassLoader);
    }

    // ------------------------------------ Implementation of Classloader

    /*
     * XXX: The javadoc for java.lang.ClassLoader says that the ClassLoader should
     * cache classes so that it can handle repeated requests for the same class. On
     * the other hand, the JLS seems to imply that each classloader is only asked to
     * load each class once. Is this a contradiction?
     *
     * Perhaps the second call only applies to classes which have been
     * garbage-collected?
     */
    /**
     * Resolves the specified name to a Class. The method loadClass() is called by
     * the virtual machine. As an abstract method, loadClass() must be defined in a
     * subclass of ClassLoader.
     *
     * @param name    the name of the desired Class.
     * @param resolve true if the Class needs to be resolved; false if the virtual
     *                machine just wants to determine whether the class exists or
     *                not
     * @return the resulting Class.
     * @exception ClassNotFoundException if the class loader cannot find a the
     *                                   requested class.
     */
    @Override
    protected synchronized Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        // The class object that will be returned.
        Class<?> c = null;

        // Use the cached value, if this class is already loaded into
        // this classloader.
        final ClassCacheEntry entry = cache.get(name);

        if (entry != null) {
            // Class found in our cache
            c = entry.loadedClass;

            if (resolve) {
                resolveClass(c);
            }

            return c;
        }

        if (!securityAllowsClass(name)) {
            return loadSystemClass(name, resolve);
        }

        // Attempt to load the class from the system
        try {
            c = loadSystemClass(name, resolve);

            if (c != null) {
                if (resolve) {
                    resolveClass(c);
                }

                return c;
            }
        } catch (final Exception e) {
            c = null;
        }

        // Try to load it from each repository
        final Enumeration<File> repEnum = repository.elements();
        final StringBuffer repoList = new StringBuffer();

        // Cache entry.
        final ClassCacheEntry classCache = new ClassCacheEntry();

        while (repEnum.hasMoreElements()) {
            byte[] classData = null;

            File file = repEnum.nextElement();
            final File[] files = SimpleFileFilter.fileOrFiles(file);

            for (final File file2 : files) {
                file = file2;

                try {
                    if (file.isDirectory()) {
                        classData = loadClassFromDirectory(file, name, classCache);
                    } else {
                        classData = loadClassFromZipfile(file, name, classCache);
                    }
                } catch (final IOException ioe) {
                    // Error while reading in data, consider it as not found
                    classData = null;
                }
            }

            if (classData != null) {
                // Define the class
                c = defineClass(name, classData, 0, classData.length);
                // Cache the result;
                classCache.loadedClass = c;
                // Origin is set by the specific loader
                classCache.lastModified = classCache.origin.lastModified();
                cache.put(name, classCache);

                // Resolve it if necessary
                if (resolve) {
                    resolveClass(c);
                }

                return c;
            }

            repoList.append(file.getAbsolutePath());

            if (repEnum.hasMoreElements()) {
                repoList.append(", ");
            }
        }

        // If not found in any repository
        throw new ClassNotFoundException("Class: " + name + " (repository contains: " + repoList.toString() + ")");
    }

    /**
     * Load a class using the system classloader.
     *
     * @exception ClassNotFoundException if the class loader cannot find a the
     *                                   requested class.
     * @exception NoClassDefFoundError   if the class loader cannot find a
     *                                   definition for the class.
     */
    private Class<?> loadSystemClass(final String name, final boolean resolve)
            throws NoClassDefFoundError, ClassNotFoundException {
        if (myParentClassLoader != null) {
            return myParentClassLoader.loadClass(name);
        }

        final Class<?> c = findSystemClass(name);
        // Throws if not found.

        // Add cache entry
        final ClassCacheEntry cacheEntry = new ClassCacheEntry();
        cacheEntry.origin = null;
        cacheEntry.loadedClass = c;
        cacheEntry.lastModified = Long.MAX_VALUE;
        cache.put(name, cacheEntry);

        if (resolve) {
            resolveClass(c);
        }

        return c;
    }

    /**
     * Checks whether a classloader is allowed to define a given class, within the
     * security manager restrictions.
     */
    // XXX: Should we perhaps also not allow classes to be dynamically
    // loaded from org.apache.jserv.*? Would it introduce security
    // problems if people could override classes here?
    // <mbp@humbug.org.au 1998-07-29>
    private boolean securityAllowsClass(final String className) {
        try {
            final SecurityManager security = System.getSecurityManager();

            if (security == null) {
                // if there's no security manager then all classes
                // are allowed to be loaded
                return true;
            }

            final int lastDot = className.lastIndexOf('.');
            // Check if we are allowed to load the class' package
            security.checkPackageDefinition((lastDot > -1) ? className.substring(0, lastDot) : "");
            // Throws if not allowed
            return true;
        } catch (final SecurityException e) {
            return false;
        }
    }

    /**
     * Tries to load the class from a directory.
     *
     * @param dir   The directory that contains classes.
     * @param name  The classname
     * @param cache The cache entry to set the file if successful.
     */
    private byte[] loadClassFromDirectory(final File dir, final String name, final ClassCacheEntry cache)
            throws IOException {
        // Translate class name to file name
        String classFileName = name.replace('.', File.separatorChar) + ".class";

        // Check for garbage input at beginning of file name
        // i.e. ../ or similar
        if (!Character.isJavaIdentifierStart(classFileName.charAt(0))) {
            // Find real beginning of class name
            int start = 1;

            while (!Character.isJavaIdentifierStart(classFileName.charAt(start))) {
                start++;
            }

            classFileName = classFileName.substring(start);
        }

        final File classFile = new File(dir, classFileName);

        if (classFile.exists()) {
            cache.origin = classFile;
            try (InputStream in = new FileInputStream(classFile)) {
                return loadBytesFromStream(in, (int) classFile.length());
            }
        } else {
            // Not found
            return null;
        }
    }

    /**
     * Tries to load the class from a zip file.
     *
     * @param file  The zipfile that contains classes.
     * @param name  The classname
     * @param cache The cache entry to set the file if successful.
     */
    private byte[] loadClassFromZipfile(final File file, final String name, final ClassCacheEntry cache)
            throws IOException {
        // Translate class name to file name
        final String classFileName = name.replace('.', '/') + ".class";

        try (ZipFile zipfile = new ZipFile(file)) {
            final ZipEntry entry = zipfile.getEntry(classFileName);
            if (entry != null) {
                cache.origin = file;
                try (InputStream is = zipfile.getInputStream(entry)) {
                    return loadBytesFromStream(is, (int) entry.getSize());
                }
            } else {
                // Not found
                return null;
            }
        }
    }

    /**
     * Loads all the bytes of an InputStream.
     */
    private byte[] loadBytesFromStream(final InputStream in, final int length) throws IOException {
        int i = length;
        final byte[] buf = new byte[i];
        int nRead, count = 0;

        while ((i > 0) && ((nRead = in.read(buf, count, i)) != -1)) {
            count += nRead;
            i -= nRead;
        }

        return buf;
    }

    /**
     * Get an InputStream on a given resource. Will return null if no resource with
     * this name is found.
     * <p>
     * The JServClassLoader translate the resource's name to a file or a zip entry.
     * It looks for the resource in all its repository entry.
     *
     * @see java.lang.Class#getResourceAsStream(String)
     * @param name the name of the resource, to be used as is.
     * @return an InputStream on the resource, or null if not found.
     */
    @SuppressWarnings("resource")
    @Override
    public InputStream getResourceAsStream(final String name) {
        // Try to load it from the system class
        InputStream s = null;

        if (myParentClassLoader != null) {
            s = myParentClassLoader.getResourceAsStream(name);
        }

        if (s == null) {
            s = getSystemResourceAsStream(name);
        }

        if (s == null) {
            // Try to find it from every repository
            final Enumeration<File> repEnum = repository.elements();

            while (repEnum.hasMoreElements()) {
                final File file = repEnum.nextElement();

                if (file.isDirectory()) {
                    s = loadResourceFromDirectory(file, name);
                } else if (name.endsWith(".initArgs")) {
                    final String parentFile = file.getParent();

                    if (parentFile != null) {
                        final File dir = new File(parentFile);
                        s = loadResourceFromDirectory(dir, name);
                    }
                } else {
                    s = loadResourceFromZipfile(file, name);
                }

                if (s != null) {
                    break;
                }
            }
        }

        return s;
    }

    /**
     * Loads resource from a directory.
     */
    private InputStream loadResourceFromDirectory(final File dir, final String name) {
        // Name of resources are always separated by /
        final String fileName = name.replace('/', File.separatorChar);
        final File resFile = new File(dir, fileName);

        if (resFile.exists()) {
            try {
                return new FileInputStream(resFile);
            } catch (final FileNotFoundException shouldnothappen) {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Loads resource from a zip file
     */
    private InputStream loadResourceFromZipfile(final File file, final String name) {
        InputStream result = null;
        try (ZipFile zipfile = new ZipFile(file)) {
            final ZipEntry entry = zipfile.getEntry(name);
            if (entry != null) {
                final long length = entry.getSize();
                try (InputStream resourceStream = zipfile.getInputStream(entry)) {
                    final byte[] data = loadBytesFromStream(resourceStream, (int) length);
                    result = new ByteArrayInputStream(data);
                }
            }
        } catch (final IOException e) {
            result = null;
        }
        return result;
    }

    /**
     * Find a resource with a given name. The return is a URL to the resource. Doing
     * a getContent() on the URL may return an Image, an AudioClip,or an
     * InputStream.
     * <p>
     * This classloader looks for the resource only in the directory repository for
     * this resource.
     *
     * @param name the name of the resource, to be used as is.
     * @return an URL on the resource, or null if not found.
     */
    @Override
    public URL getResource(final String name) {

        if (name == null) {
            return null;
        }

        // First ask the primordial class loader to fetch it from the classpath
        URL u = null;

        if (myParentClassLoader != null) {
            u = myParentClassLoader.getResource(name);
        }

        if (u == null) {
            u = getSystemResource(name);
        }

        if (u != null) {
            return u;
        }

        // We got here so we have to look for the resource in our list of repository
        // elements
        final Enumeration<File> repEnum = repository.elements();

        while (repEnum.hasMoreElements()) {
            final File file = repEnum.nextElement();

            // Construct a file://-URL if the repository is a directory
            if (file.isDirectory()) {
                final String fileName = name.replace('/', File.separatorChar);
                final File resFile = new File(file, fileName);

                if (resFile.exists()) {
                    // Build a file:// URL form the file name
                    try {
                        return new URL("file", null, resFile.getAbsolutePath());
                    } catch (final java.net.MalformedURLException badurl) {
                        badurl.printStackTrace();
                        return null;
                    }
                }
            } else {
                // a jar:-URL *could* change even between minor releases, but
                // didn't between JVM's 1.1.6 and 1.3beta. Tested on JVM's from
                // IBM, Blackdown, Microsoft, Sun @ Windows and Sun @ Solaris
                try {
                    try (ZipFile zf = new ZipFile(file.getAbsolutePath())) {
                        final ZipEntry ze = zf.getEntry(name);
                        if (ze != null) {
                            try {
                                return new URL("jar:file:" + file.getAbsolutePath() + "!/" + name);
                            } catch (final java.net.MalformedURLException badurl) {
                                badurl.printStackTrace();
                                return null;
                            }
                        }
                    }
                } catch (final IOException ioe) {
                    ioe.printStackTrace();
                    return null;
                }
            }
        }

        // Not found
        return null;
    }

    /**
     * Return the last modified time for a class in the ClassCache.
     *
     * @param name the classname to find the last modified time for
     * @return the timestamp for when it was last modified
     * @throws ClassNotFoundException if class is not found
     */
    public long lastModified(final String name) throws ClassNotFoundException {
        final ClassCacheEntry entry = cache.get(name);

        if (entry == null) {
            throw new ClassNotFoundException("Could not find class: " + name);
        } else {
            return entry.lastModified;
        }
    }
}
