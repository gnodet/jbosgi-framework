package org.jboss.test.osgi.framework.simple.bundleB;
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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A Service Activator
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Apr-2009
 */
public class SimpleLogServiceActivator implements BundleActivator {

    public void start(BundleContext context) {
        final String symName = context.getBundle().getSymbolicName();
        addMessage(symName, "startBundleActivator");

        ServiceReference sref = context.getServiceReference(LogService.class.getName());
        if (sref != null) {
            LogService service = (LogService) context.getService(sref);
            String message = "getService: " + service.getClass().getName();
            addMessage(symName, message);
        }

        ServiceTracker tracker = new ServiceTracker(context, LogService.class.getName(), null) {

            @Override
            public Object addingService(ServiceReference reference) {
                LogService service = (LogService) super.addingService(reference);
                String message = "addingService: " + service.getClass().getName();
                addMessage(symName, message);
                return service;
            }
        };
        tracker.open();
    }

    public void stop(BundleContext context) {
        String symName = context.getBundle().getSymbolicName();
        addMessage(symName, "stopBundleActivator");
    }

    private void addMessage(String propName, String message) {
        String previous = System.getProperty(propName, ":");
        System.setProperty(propName, previous + message + ":");
        // System.out.println(message);
    }
}