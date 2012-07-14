/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 021101301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework;


/**
 * A collection of propriatary constants.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
public interface Constants extends org.osgi.framework.Constants {

    /** The prefix for modules/services managed by the OSGi layer */
    String JBOSGI_PREFIX = "jbosgi";

    /** The number of threads available for MSC services */
    String PROPERTY_FRAMEWORK_BOOTSTRAP_THREADS = "org.jboss.osgi.framework.bootstrap.maxThreads";

    /** The timeout in milliseconds for the framework to initialize */
    String PROPERTY_FRAMEWORK_INIT_TIMEOUT = "org.jboss.osgi.framework.init.timeout";

    /** The timeout in milliseconds for the framework to start */
    String PROPERTY_FRAMEWORK_START_TIMEOUT = "org.jboss.osgi.framework.start.timeout";

    /** A list of URLs to bundles that get installed on framework startup */
    String PROPERTY_AUTO_INSTALL_URLS = "org.jboss.osgi.auto.install";

    /** A list of URLs to bundles that get installed and started on framework startup */
    String PROPERTY_AUTO_START_URLS = "org.jboss.osgi.auto.start";

    /** The default timeout for the framework to initialize is 5sec */
    int DEFAULT_FRAMEWORK_INIT_TIMEOUT = 5000;

    /** The default timeout for the framework to start is 10sec */
    int DEFAULT_FRAMEWORK_START_TIMEOUT = 10000;
}