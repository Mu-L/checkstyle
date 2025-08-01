///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2025 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.BeforeExecutionFileFilter;
import com.puppycrawl.tools.checkstyle.api.BeforeExecutionFileFilterSet;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.api.Context;
import com.puppycrawl.tools.checkstyle.api.ExternalResourceHolder;
import com.puppycrawl.tools.checkstyle.api.FileSetCheck;
import com.puppycrawl.tools.checkstyle.api.FileText;
import com.puppycrawl.tools.checkstyle.api.Filter;
import com.puppycrawl.tools.checkstyle.api.FilterSet;
import com.puppycrawl.tools.checkstyle.api.MessageDispatcher;
import com.puppycrawl.tools.checkstyle.api.RootModule;
import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import com.puppycrawl.tools.checkstyle.api.SeverityLevelCounter;
import com.puppycrawl.tools.checkstyle.api.Violation;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;

/**
 * This class provides the functionality to check a set of files.
 */
public class Checker extends AbstractAutomaticBean implements MessageDispatcher, RootModule {

    /** Message to use when an exception occurs and should be printed as a violation. */
    public static final String EXCEPTION_MSG = "general.exception";

    /** The extension separator. */
    private static final String EXTENSION_SEPARATOR = ".";

    /** Logger for Checker. */
    private final Log log;

    /** Maintains error count. */
    private final SeverityLevelCounter counter = new SeverityLevelCounter(
            SeverityLevel.ERROR);

    /** Vector of listeners. */
    private final List<AuditListener> listeners = new ArrayList<>();

    /** Vector of fileset checks. */
    private final List<FileSetCheck> fileSetChecks = new ArrayList<>();

    /** The audit event before execution file filters. */
    private final BeforeExecutionFileFilterSet beforeExecutionFileFilters =
            new BeforeExecutionFileFilterSet();

    /** The audit event filters. */
    private final FilterSet filters = new FilterSet();

    /** The basedir to strip off in file names. */
    private String basedir;

    /** Locale country to report messages . **/
    @XdocsPropertyType(PropertyType.LOCALE_COUNTRY)
    private String localeCountry = Locale.getDefault().getCountry();
    /** Locale language to report messages . **/
    @XdocsPropertyType(PropertyType.LOCALE_LANGUAGE)
    private String localeLanguage = Locale.getDefault().getLanguage();

    /** The factory for instantiating submodules. */
    private ModuleFactory moduleFactory;

    /** The classloader used for loading Checkstyle module classes. */
    private ClassLoader moduleClassLoader;

    /** The context of all child components. */
    private Context childContext;

    /** The file extensions that are accepted. */
    private String[] fileExtensions;

    /**
     * The severity level of any violations found by submodules.
     * The value of this property is passed to submodules via
     * contextualize().
     *
     * <p>Note: Since the Checker is merely a container for modules
     * it does not make sense to implement logging functionality
     * here. Consequently, Checker does not extend AbstractViolationReporter,
     * leading to a bit of duplicated code for severity level setting.
     */
    private SeverityLevel severity = SeverityLevel.ERROR;

    /** Name of a charset. */
    private String charset = StandardCharsets.UTF_8.name();

    /** Cache file. **/
    @XdocsPropertyType(PropertyType.FILE)
    private PropertyCacheFile cacheFile;

    /** Controls whether exceptions should halt execution or not. */
    private boolean haltOnException = true;

    /** The tab width for column reporting. */
    private int tabWidth = CommonUtil.DEFAULT_TAB_WIDTH;

    /**
     * Creates a new {@code Checker} instance.
     * The instance needs to be contextualized and configured.
     */
    public Checker() {
        addListener(counter);
        log = LogFactory.getLog(Checker.class);
    }

    /**
     * Sets cache file.
     *
     * @param fileName the cache file.
     * @throws IOException if there are some problems with file loading.
     */
    public void setCacheFile(String fileName) throws IOException {
        final Configuration configuration = getConfiguration();
        cacheFile = new PropertyCacheFile(configuration, fileName);
        cacheFile.load();
    }

    /**
     * Removes before execution file filter.
     *
     * @param filter before execution file filter to remove.
     */
    public void removeBeforeExecutionFileFilter(BeforeExecutionFileFilter filter) {
        beforeExecutionFileFilters.removeBeforeExecutionFileFilter(filter);
    }

    /**
     * Removes filter.
     *
     * @param filter filter to remove.
     */
    public void removeFilter(Filter filter) {
        filters.removeFilter(filter);
    }

    @Override
    public void destroy() {
        listeners.clear();
        fileSetChecks.clear();
        beforeExecutionFileFilters.clear();
        filters.clear();
        if (cacheFile != null) {
            try {
                cacheFile.persist();
            }
            catch (IOException exc) {
                throw new IllegalStateException("Unable to persist cache file.", exc);
            }
        }
    }

    /**
     * Removes a given listener.
     *
     * @param listener a listener to remove
     */
    public void removeListener(AuditListener listener) {
        listeners.remove(listener);
    }

    /**
     * Sets base directory.
     *
     * @param basedir the base directory to strip off in file names
     */
    public void setBasedir(String basedir) {
        this.basedir = basedir;
    }

    @Override
    public int process(List<File> files) throws CheckstyleException {
        if (cacheFile != null) {
            cacheFile.putExternalResources(getExternalResourceLocations());
        }

        // Prepare to start
        fireAuditStarted();
        for (final FileSetCheck fsc : fileSetChecks) {
            fsc.beginProcessing(charset);
        }

        final List<File> targetFiles = files.stream()
                .filter(file -> CommonUtil.matchesFileExtension(file, fileExtensions))
                .toList();
        processFiles(targetFiles);

        // Finish up
        // It may also log!!!
        fileSetChecks.forEach(FileSetCheck::finishProcessing);

        // It may also log!!!
        fileSetChecks.forEach(FileSetCheck::destroy);

        final int errorCount = counter.getCount();
        fireAuditFinished();
        return errorCount;
    }

    /**
     * Returns a set of external configuration resource locations which are used by all file set
     * checks and filters.
     *
     * @return a set of external configuration resource locations which are used by all file set
     *         checks and filters.
     */
    private Set<String> getExternalResourceLocations() {
        return Stream.concat(fileSetChecks.stream(), filters.getFilters().stream())
            .filter(ExternalResourceHolder.class::isInstance)
            .flatMap(resource -> {
                return ((ExternalResourceHolder) resource)
                        .getExternalResourceLocations().stream();
            })
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Notify all listeners about the audit start. */
    private void fireAuditStarted() {
        final AuditEvent event = new AuditEvent(this);
        for (final AuditListener listener : listeners) {
            listener.auditStarted(event);
        }
    }

    /** Notify all listeners about the audit end. */
    private void fireAuditFinished() {
        final AuditEvent event = new AuditEvent(this);
        for (final AuditListener listener : listeners) {
            listener.auditFinished(event);
        }
    }

    /**
     * Processes a list of files with all FileSetChecks.
     *
     * @param files a list of files to process.
     * @throws CheckstyleException if error condition within Checkstyle occurs.
     * @throws Error wraps any java.lang.Error happened during execution
     * @noinspection ProhibitedExceptionThrown
     * @noinspectionreason ProhibitedExceptionThrown - There is no other way to
     *      deliver filename that was under processing.
     */
    // -@cs[CyclomaticComplexity] no easy way to split this logic of processing the file
    private void processFiles(List<File> files) throws CheckstyleException {
        for (final File file : files) {
            String fileName = null;
            final String filePath = file.getPath();
            try {
                fileName = file.getAbsolutePath();
                final long timestamp = file.lastModified();
                if (cacheFile != null && cacheFile.isInCache(fileName, timestamp)
                        || !acceptFileStarted(fileName)) {
                    continue;
                }
                if (cacheFile != null) {
                    cacheFile.put(fileName, timestamp);
                }
                fireFileStarted(fileName);
                final SortedSet<Violation> fileMessages = processFile(file);
                fireErrors(fileName, fileMessages);
                fireFileFinished(fileName);
            }
            // -@cs[IllegalCatch] There is no other way to deliver filename that was under
            // processing. See https://github.com/checkstyle/checkstyle/issues/2285
            catch (Exception exc) {
                if (fileName != null && cacheFile != null) {
                    cacheFile.remove(fileName);
                }

                // We need to catch all exceptions to put a reason failure (file name) in exception
                throw new CheckstyleException(
                        getLocalizedMessage("Checker.processFilesException", filePath), exc);
            }
            catch (Error error) {
                if (fileName != null && cacheFile != null) {
                    cacheFile.remove(fileName);
                }

                // We need to catch all errors to put a reason failure (file name) in error
                throw new Error("Error was thrown while processing " + filePath, error);
            }
        }
    }

    /**
     * Processes a file with all FileSetChecks.
     *
     * @param file a file to process.
     * @return a sorted set of violations to be logged.
     * @throws CheckstyleException if error condition within Checkstyle occurs.
     * @noinspection ProhibitedExceptionThrown
     * @noinspectionreason ProhibitedExceptionThrown - there is no other way to obey
     *      haltOnException field
     */
    private SortedSet<Violation> processFile(File file) throws CheckstyleException {
        final SortedSet<Violation> fileMessages = new TreeSet<>();
        try {
            final FileText theText = new FileText(file.getAbsoluteFile(), charset);
            for (final FileSetCheck fsc : fileSetChecks) {
                fileMessages.addAll(fsc.process(file, theText));
            }
        }
        catch (final IOException ioe) {
            log.debug("IOException occurred.", ioe);
            fileMessages.add(new Violation(1,
                    Definitions.CHECKSTYLE_BUNDLE, EXCEPTION_MSG,
                    new String[] {ioe.getMessage()}, null, getClass(), null));
        }
        // -@cs[IllegalCatch] There is no other way to obey haltOnException field
        catch (Exception exc) {
            if (haltOnException) {
                throw exc;
            }

            log.debug("Exception occurred.", exc);

            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw, true);

            exc.printStackTrace(pw);

            fileMessages.add(new Violation(1,
                    Definitions.CHECKSTYLE_BUNDLE, EXCEPTION_MSG,
                    new String[] {sw.getBuffer().toString()},
                    null, getClass(), null));
        }
        return fileMessages;
    }

    /**
     * Check if all before execution file filters accept starting the file.
     *
     * @param fileName
     *            the file to be audited
     * @return {@code true} if the file is accepted.
     */
    private boolean acceptFileStarted(String fileName) {
        final String stripped = CommonUtil.relativizePath(basedir, fileName);
        return beforeExecutionFileFilters.accept(stripped);
    }

    /**
     * Notify all listeners about the beginning of a file audit.
     *
     * @param fileName
     *            the file to be audited
     */
    @Override
    public void fireFileStarted(String fileName) {
        final String stripped = CommonUtil.relativizePath(basedir, fileName);
        final AuditEvent event = new AuditEvent(this, stripped);
        for (final AuditListener listener : listeners) {
            listener.fileStarted(event);
        }
    }

    /**
     * Notify all listeners about the errors in a file.
     *
     * @param fileName the audited file
     * @param errors the audit errors from the file
     */
    @Override
    public void fireErrors(String fileName, SortedSet<Violation> errors) {
        final String stripped = CommonUtil.relativizePath(basedir, fileName);
        boolean hasNonFilteredViolations = false;
        for (final Violation element : errors) {
            final AuditEvent event = new AuditEvent(this, stripped, element);
            if (filters.accept(event)) {
                hasNonFilteredViolations = true;
                for (final AuditListener listener : listeners) {
                    listener.addError(event);
                }
            }
        }
        if (hasNonFilteredViolations && cacheFile != null) {
            cacheFile.remove(fileName);
        }
    }

    /**
     * Notify all listeners about the end of a file audit.
     *
     * @param fileName
     *            the audited file
     */
    @Override
    public void fireFileFinished(String fileName) {
        final String stripped = CommonUtil.relativizePath(basedir, fileName);
        final AuditEvent event = new AuditEvent(this, stripped);
        for (final AuditListener listener : listeners) {
            listener.fileFinished(event);
        }
    }

    @Override
    protected void finishLocalSetup() throws CheckstyleException {
        final Locale locale = new Locale(localeLanguage, localeCountry);
        LocalizedMessage.setLocale(locale);

        if (moduleFactory == null) {
            if (moduleClassLoader == null) {
                throw new CheckstyleException(getLocalizedMessage("Checker.finishLocalSetup"));
            }

            final Set<String> packageNames = PackageNamesLoader
                    .getPackageNames(moduleClassLoader);
            moduleFactory = new PackageObjectFactory(packageNames,
                    moduleClassLoader);
        }

        final DefaultContext context = new DefaultContext();
        context.add("charset", charset);
        context.add("moduleFactory", moduleFactory);
        context.add("severity", severity.getName());
        context.add("basedir", basedir);
        context.add("tabWidth", String.valueOf(tabWidth));
        childContext = context;
    }

    /**
     * {@inheritDoc} Creates child module.
     *
     * @noinspection ChainOfInstanceofChecks
     * @noinspectionreason ChainOfInstanceofChecks - we treat checks and filters differently
     */
    @Override
    protected void setupChild(Configuration childConf)
            throws CheckstyleException {
        final String name = childConf.getName();
        final Object child;

        try {
            child = moduleFactory.createModule(name);

            if (child instanceof AbstractAutomaticBean bean) {
                bean.contextualize(childContext);
                bean.configure(childConf);
            }
        }
        catch (final CheckstyleException exc) {
            throw new CheckstyleException(
                    getLocalizedMessage("Checker.setupChildModule", name, exc.getMessage()), exc);
        }
        if (child instanceof FileSetCheck fsc) {
            fsc.init();
            addFileSetCheck(fsc);
        }
        else if (child instanceof BeforeExecutionFileFilter filter) {
            addBeforeExecutionFileFilter(filter);
        }
        else if (child instanceof Filter filter) {
            addFilter(filter);
        }
        else if (child instanceof AuditListener listener) {
            addListener(listener);
        }
        else {
            throw new CheckstyleException(
                    getLocalizedMessage("Checker.setupChildNotAllowed", name));
        }
    }

    /**
     * Adds a FileSetCheck to the list of FileSetChecks
     * that is executed in process().
     *
     * @param fileSetCheck the additional FileSetCheck
     */
    public void addFileSetCheck(FileSetCheck fileSetCheck) {
        fileSetCheck.setMessageDispatcher(this);
        fileSetChecks.add(fileSetCheck);
    }

    /**
     * Adds a before execution file filter to the end of the event chain.
     *
     * @param filter the additional filter
     */
    public void addBeforeExecutionFileFilter(BeforeExecutionFileFilter filter) {
        beforeExecutionFileFilters.addBeforeExecutionFileFilter(filter);
    }

    /**
     * Adds a filter to the end of the audit event filter chain.
     *
     * @param filter the additional filter
     */
    public void addFilter(Filter filter) {
        filters.addFilter(filter);
    }

    @Override
    public final void addListener(AuditListener listener) {
        listeners.add(listener);
    }

    /**
     * Sets the file extensions that identify the files that pass the
     * filter of this FileSetCheck.
     *
     * @param extensions the set of file extensions. A missing
     *     initial '.' character of an extension is automatically added.
     */
    public final void setFileExtensions(String... extensions) {
        if (extensions != null) {
            fileExtensions = new String[extensions.length];
            for (int i = 0; i < extensions.length; i++) {
                final String extension = extensions[i];
                if (extension.startsWith(EXTENSION_SEPARATOR)) {
                    fileExtensions[i] = extension;
                }
                else {
                    fileExtensions[i] = EXTENSION_SEPARATOR + extension;
                }
            }
        }
    }

    /**
     * Sets the factory for creating submodules.
     *
     * @param moduleFactory the factory for creating FileSetChecks
     */
    public void setModuleFactory(ModuleFactory moduleFactory) {
        this.moduleFactory = moduleFactory;
    }

    /**
     * Sets locale country.
     *
     * @param localeCountry the country to report messages
     */
    public void setLocaleCountry(String localeCountry) {
        this.localeCountry = localeCountry;
    }

    /**
     * Sets locale language.
     *
     * @param localeLanguage the language to report messages
     */
    public void setLocaleLanguage(String localeLanguage) {
        this.localeLanguage = localeLanguage;
    }

    /**
     * Sets the severity level.  The string should be one of the names
     * defined in the {@code SeverityLevel} class.
     *
     * @param severity  The new severity level
     * @see SeverityLevel
     */
    public final void setSeverity(String severity) {
        this.severity = SeverityLevel.getInstance(severity);
    }

    @Override
    public final void setModuleClassLoader(ClassLoader moduleClassLoader) {
        this.moduleClassLoader = moduleClassLoader;
    }

    /**
     * Sets a named charset.
     *
     * @param charset the name of a charset
     * @throws UnsupportedEncodingException if charset is unsupported.
     */
    public void setCharset(String charset)
            throws UnsupportedEncodingException {
        if (!Charset.isSupported(charset)) {
            throw new UnsupportedEncodingException(
                    getLocalizedMessage("Checker.setCharset", charset));
        }
        this.charset = charset;
    }

    /**
     * Sets the field haltOnException.
     *
     * @param haltOnException the new value.
     */
    public void setHaltOnException(boolean haltOnException) {
        this.haltOnException = haltOnException;
    }

    /**
     * Set the tab width to report audit events with.
     *
     * @param tabWidth an {@code int} value
     */
    public final void setTabWidth(int tabWidth) {
        this.tabWidth = tabWidth;
    }

    /**
     * Clears the cache.
     */
    public void clearCache() {
        if (cacheFile != null) {
            cacheFile.reset();
        }
    }

    /**
     * Extracts localized messages from properties files.
     *
     * @param messageKey the key pointing to localized message in respective properties file.
     * @param args the arguments of message in respective properties file.
     * @return a string containing extracted localized message
     */
    private String getLocalizedMessage(String messageKey, Object... args) {
        final LocalizedMessage localizedMessage = new LocalizedMessage(
            Definitions.CHECKSTYLE_BUNDLE, getClass(),
                    messageKey, args);

        return localizedMessage.getMessage();
    }

}
