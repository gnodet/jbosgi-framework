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
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.modules.Module;
import org.jboss.modules.log.JDKModuleLogger;
import org.jboss.modules.log.ModuleLogger;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.osgi.framework.launch.Framework;

/**
 * A builder for the {@link Framework} implementation. Provides hooks for various integration aspects.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Mar-2011
 */
public final class FrameworkBuilder {

    private final Map<String, Object> initialProperties = new HashMap<String, Object>();
    private ServiceContainer serviceContainer;
    private ServiceTarget serviceTarget;
    private boolean closed;

    public FrameworkBuilder(Map<String, Object> props) {
        if (props != null) {
            initialProperties.putAll(props);
        }
    }

    public Object getProperty(String key) {
        return getProperty(key, null);
    }

    public Object getProperty(String key, Object defaultValue) {
        Object value = initialProperties.get(key);
        return value != null ? value : defaultValue;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(initialProperties);
    }

    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    public void setServiceContainer(ServiceContainer serviceContainer) {
        assertNotClosed();
        this.serviceContainer = serviceContainer;
    }

    public ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    public void setServiceTarget(ServiceTarget serviceTarget) {
        assertNotClosed();
        this.serviceTarget = serviceTarget;
    }

    public Framework createFramework() {
        assertNotClosed();
        return new FrameworkProxy(this);
    }

    public void createFrameworkServices(boolean firstInit) {
        assertNotClosed();
        createFrameworkServicesInternal(serviceContainer, serviceTarget, firstInit);
    }

    void createFrameworkServicesInternal(ServiceRegistry serviceRegistry, ServiceTarget serviceTarget, boolean firstInit) {
        try {
            // Do this first so this URLStreamHandlerFactory gets installed
            URLHandlerPlugin.addService(serviceTarget);

            // Setup the logging system for jboss-modules
            if (getProperty(ModuleLogger.class.getName()) == null) {
                Module.setModuleLogger(new JDKModuleLogger());
            }

            BundleManagerPlugin bundleManager = BundleManagerPlugin.addService(serviceTarget, this);
            FrameworkState frameworkState = FrameworkCreate.addService(serviceTarget, bundleManager);

            BundleStoragePlugin.addService(serviceTarget, firstInit);
            DeploymentFactoryPlugin.addService(serviceTarget);
            EnvironmentPlugin.addService(serviceTarget);
            FrameworkActive.addService(serviceTarget);
            FrameworkCoreServices.addService(serviceTarget);
            FrameworkEventsPlugin.addService(serviceTarget);
            FrameworkInit.addService(serviceTarget);
            LifecycleInterceptorPlugin.addService(serviceTarget);
            ModuleManagerPlugin.addService(serviceTarget);
            NativeCodePlugin.addService(serviceTarget);
            PackageAdminPlugin.addService(serviceTarget);
            ResolverPlugin.addService(serviceTarget);
            ServiceManagerPlugin.addService(serviceTarget);
            StartLevelPlugin.addService(serviceTarget);
            DefaultStorageStatePlugin.addService(serviceTarget);
            SystemBundleService.addService(serviceTarget, frameworkState);
            SystemContextService.addService(serviceTarget);

            DefaultBundleInstallPlugin.addIntegrationService(serviceRegistry, serviceTarget);
            DefaultFrameworkModulePlugin.addIntegrationService(serviceRegistry, serviceTarget);
            DefaultModuleLoaderPlugin.addIntegrationService(serviceRegistry, serviceTarget);
            DefaultSystemPathsPlugin.addIntegrationService(serviceRegistry, serviceTarget, this);
            DefaultSystemServicesPlugin.addIntegrationService(serviceRegistry, serviceTarget);

        } finally {
            closed = true;
        }
    }

    private void assertNotClosed() {
        if (closed == true)
            throw MESSAGES.illegalStateFrameworkBuilderClosed();
    }
}