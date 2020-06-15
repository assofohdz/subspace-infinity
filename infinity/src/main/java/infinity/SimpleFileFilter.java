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
package infinity;

/*
    Copyright (c) 1997-1999 The Java Apache Project.  All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions
    are met:

    1. Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    2. Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the
      distribution.

    3. All advertising materials mentioning features or use of this
      software must display the following acknowledgment:
      "This product includes software developed by the Java Apache
      Project for use in the Apache JServ servlet engine project
      <http://java.apache.org/>."

    4. The names "Apache JServ", "Apache JServ Servlet Engine" and
      "Java Apache Project" must not be used to endorse or promote products
      derived from this software without prior written permission.

    5. Products derived from this software may not be called "Apache JServ"
      nor may "Apache" nor "Apache JServ" appear in their names without
      prior written permission of the Java Apache Project.

    6. Redistributions of any form whatsoever must retain the following
      acknowledgment:
      "This product includes software developed by the Java Apache
      Project for use in the Apache JServ servlet engine project
      <http://java.apache.org/>."

    THIS SOFTWARE IS PROVIDED BY THE JAVA APACHE PROJECT "AS IS" AND ANY
    EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
    PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE JAVA APACHE PROJECT OR
    ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
    SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
    NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
    HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
    STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
    OF THE POSSIBILITY OF SUCH DAMAGE.

    This software consists of voluntary contributions made by many
    individuals on behalf of the Java Apache Group. For more information
    on the Java Apache Project and the Apache JServ Servlet Engine project,
    please see <http://java.apache.org/>.

 */
import java.io.File;
import java.io.FilenameFilter;

/**
 * Class that implements the java.io.FilenameFilter interface.
 *
 * @author <a href="mailto:mjenning@islandnet.com">Mike Jennings</a>
 * @version $Revision: 9187 $
 */
public class SimpleFileFilter implements FilenameFilter {

    private String[] extensions;

    public SimpleFileFilter(String ext) {
        this(new String[]{ext});
    }

    public SimpleFileFilter(String[] exts) {
        extensions = new String[exts.length];

        for (int i = 0; i < exts.length; i++) {
            extensions[i] = exts[i].toLowerCase();
        }
    }

    /**
     * filenamefilter interface method
     */
    public boolean accept(File dir, String _name) {
        String name = _name.toLowerCase();

        for (int i = 0; i < extensions.length; i++) {
            if (name.endsWith(extensions[i])) {
                return true;
            }
        }

        return false;
    }

    /**
     * this method checks to see if an asterisk is imbedded in the filename, if
     * it is, it does an "ls" or "dir" of the parent directory returning a list
     * of files that match eg. /usr/home/mjennings/*.jar would expand out to all
     * of the files with a .jar extension in the /usr/home/mjennings directory
     * @param f the filename
     * @return the matching files in the directory
     */
    public static File[] fileOrFiles(File f) {
        if (f == null) {
            return null;
        }

        String parentstring = f.getParent();
        File parent;

        if (parentstring != null) {
            parent = new File(parentstring);
        } else {
            parent = new File("");
        }

        String fname = f.getName();
        File[] files;

        if (fname.length() > 0 && fname.charAt(0) == '*') {
            String filter = fname.substring(1, fname.length());
            String[] names = parent.list(new SimpleFileFilter(filter));

            if (names == null) {
                return null;
            }

            files = new File[names.length];

            for (int i = 0; i < names.length; i++) {
                files[i] = new File(parent.getPath() + File.separator + names[i]);
            }

            return files;
        } else {
            files = new File[1];
            files[0] = f;
            return files;
        }
    }
}
