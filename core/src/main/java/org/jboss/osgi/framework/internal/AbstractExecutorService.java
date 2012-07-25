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

import java.util.concurrent.ExecutorService;

import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;


/**
 * Plugin that provides an ExecutorService.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Mar-2011
 */
abstract class AbstractExecutorService<T> extends AbstractPluginService<T> {

    private ExecutorService executorService;

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        executorService = createExecutorService();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        executorService.shutdown();
    }

    abstract ExecutorService createExecutorService();

    ExecutorService getExecutorService() {
        return executorService;
    }

    void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
}