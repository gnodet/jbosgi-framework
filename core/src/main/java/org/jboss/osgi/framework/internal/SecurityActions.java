package org.jboss.osgi.framework.internal;
/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Privileged actions used by this package.
 * No methods in this class are to be made public under any circumstances!
 *
 * @author Thomas.Diesler@jboss.com
 * @since 29-Oct-2010
 */
final class SecurityActions {

    // Hide ctor
    private SecurityActions() {
    }

    /**
     * Get the thread context class loader
     */
    static ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                Thread currentThread = Thread.currentThread();
                return currentThread.getContextClassLoader();
            }
        });
    }

    /**
     * Set the thread context class loader
     */
    static Void setContextClassLoader(final ClassLoader classLoader) {
        return AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                Thread currentThread = Thread.currentThread();
                currentThread.setContextClassLoader(classLoader);
                return null;
            }
        });
    }

    static String getSystemProperty(final String key, final String defaultValue) {
        if (System.getSecurityManager() == null) {
            String value = System.getProperty(key);
            return value != null ? value : defaultValue;
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    String value = System.getProperty(key);
                    return value != null ? value : defaultValue;
                }
            });
        }
    }

    static void setSystemProperty(final String key, final String value) {
        if (System.getSecurityManager() == null) {
            System.setProperty(key, value);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    System.setProperty(key, value);
                    return null;
                }
            });
        }
    }
}
