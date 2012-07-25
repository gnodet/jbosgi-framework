package org.jboss.test.osgi.framework.service.support;
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

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * SimpleServiceFactory.
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 1.1 $
 */
public class SimpleServiceFactory implements ServiceFactory {

    private Object service;
    private Throwable throwOnGetService;

    public Bundle getBundle;
    public int getCount;

    public Bundle ungetBundle;
    public ServiceRegistration ungetRegistration;
    public Object ungetService;
    public int ungetCount;

    public SimpleServiceFactory(Object service, Throwable throwOnGetService) {
        this.service = service;
        this.throwOnGetService = throwOnGetService;
    }

    public Object getService(Bundle bundle, ServiceRegistration registration) {
        if (throwOnGetService instanceof RuntimeException)
            throw (RuntimeException) throwOnGetService;
        if (throwOnGetService instanceof Error)
            throw (Error) throwOnGetService;

        getBundle = bundle;
        getCount++;
        return service;
    }

    public void ungetService(Bundle bundle, ServiceRegistration registration, Object unget) {
        ungetBundle = bundle;
        ungetRegistration = registration;
        ungetService = unget;
        ungetCount++;
    }

}
