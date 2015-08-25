/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.framework.searchpolicy;

import org.apache.felix.framework.Logger;
import org.apache.felix.framework.cache.BundleRevision;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

public class R4Library
{
    private Logger m_logger = null;
    private BundleRevision m_revision = null;
    private String m_libraryFile = null;
    private String[] m_osnames = null;
    private String[] m_processors = null;
    private String[] m_osversions = null;
    private String[] m_languages = null;
    private String m_selectionFilter = null;

    public R4Library(Logger logger, BundleRevision revision,
        String libraryFile, String[] osnames, String[] processors, String[] osversions,
        String[] languages, String selectionFilter)
    {
        m_logger = logger;
        m_revision = revision;
        m_libraryFile = libraryFile;
        m_osnames = osnames;
        m_processors = processors;
        m_osversions = osversions;
        m_languages = languages;
        m_selectionFilter = selectionFilter;
    }

    public String[] getOSNames()
    {
        return m_osnames;
    }

    public String[] getProcessors()
    {
        return m_processors;
    }

    public String[] getOSVersions()
    {
        return m_osversions;
    }

    public String[] getLanguages()
    {
        return m_languages;
    }

    public String getSelectionFilter()
    {
        return m_selectionFilter;
    }

    /**
     * <p>
     * Returns a file system path to the specified library.
     * </p>
     * 
     * @param name the name of the library that is being requested.
     * @return a file system path to the specified library.
     */
    public String getPath(String name)
    {
        String libname = System.mapLibraryName(name);
        if (m_libraryFile.indexOf(libname) >= 0)
        {
            try
            {
                return m_revision.findLibrary(m_libraryFile);
            }
            catch (Exception ex)
            {
                m_logger.log(Logger.LOG_ERROR, "R4Library: Finding library '"
                    + name + "'.", new BundleException(
                    "Unable to find native library '" + name + "'."));
            }
        }
        return null;
    }

    public String toString()
    {
        if (m_libraryFile != null)
        {
            StringBuffer sb = new StringBuffer();
            sb.append(m_libraryFile);
            sb.append(';');
            for (int i = 0; (m_osnames != null) && (i < m_osnames.length); i++)
            {
                sb.append(Constants.BUNDLE_NATIVECODE_OSNAME);
                sb.append('=');
                sb.append(m_osnames[i]);
                sb.append(';');
            }
            for (int i = 0; (m_processors != null) && (i < m_processors.length); i++)
            {
                sb.append(Constants.BUNDLE_NATIVECODE_PROCESSOR);
                sb.append(m_processors[i]);
                sb.append(';');
            }
            for (int i = 0; (m_osversions != null) && (i < m_osversions.length); i++)
            {
                sb.append(Constants.BUNDLE_NATIVECODE_OSVERSION);
                sb.append(m_osversions[i]);
                sb.append(';');
            }
            for (int i = 0; (m_languages != null) && (i < m_languages.length); i++)
            {
                sb.append(Constants.BUNDLE_NATIVECODE_LANGUAGE);
                sb.append(m_languages[i]);
                sb.append(';');
            }
            sb.append(Constants.SELECTION_FILTER_ATTRIBUTE);
            sb.append('=');
            sb.append('\'');
            sb.append(m_selectionFilter);
            sb.append('\'');

            return sb.toString();
        }
        return "*";
    }
}