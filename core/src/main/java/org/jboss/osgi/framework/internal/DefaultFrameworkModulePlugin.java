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

import static org.jboss.osgi.framework.IntegrationServices.FRAMEWORK_MODULE_PLUGIN;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.FrameworkModulePlugin;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.SystemPathsPlugin;
import org.osgi.framework.Bundle;

/**
 * The system module provider plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
final class DefaultFrameworkModulePlugin extends AbstractPluginService<FrameworkModulePlugin> implements FrameworkModulePlugin {

    private static final ModuleIdentifier FRAMEWORK_MODULE_IDENTIFIER = ModuleIdentifier.create(Constants.JBOSGI_PREFIX + ".framework");
    private final InjectedValue<SystemPathsPlugin> injectedSystemPaths = new InjectedValue<SystemPathsPlugin>();

    private Module frameworkModule;

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(FRAMEWORK_MODULE_PLUGIN) == null) {
            DefaultFrameworkModulePlugin service = new DefaultFrameworkModulePlugin();
            ServiceBuilder<FrameworkModulePlugin> builder = serviceTarget.addService(FRAMEWORK_MODULE_PLUGIN, service);
            builder.addDependency(IntegrationServices.SYSTEM_PATHS_PLUGIN, SystemPathsPlugin.class, service.injectedSystemPaths);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }
    }

    private DefaultFrameworkModulePlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        frameworkModule = null;
    }

    @Override
    public FrameworkModulePlugin getValue() {
        return this;
    }

    @Override
    public Module getFrameworkModule(Bundle bundle) {
        if (frameworkModule == null) {
            SystemBundleState systemBundle = (SystemBundleState) bundle;
            frameworkModule = createFrameworkModule(systemBundle);
        }
        return frameworkModule;
    }

    private Module createFrameworkModule(SystemBundleState systemBundle) {

        ModuleSpec.Builder specBuilder = ModuleSpec.build(FRAMEWORK_MODULE_IDENTIFIER);
        SystemPathsPlugin plugin = injectedSystemPaths.getValue();
        Set<String> bootPaths = plugin.getBootDelegationPaths();
        PathFilter bootFilter = plugin.getBootDelegationFilter();
        PathFilter acceptAll = PathFilters.acceptAll();
        specBuilder.addDependency(DependencySpec.createSystemDependencySpec(bootFilter, acceptAll, bootPaths));

        final ClassLoader classLoader = BundleManagerPlugin.class.getClassLoader();
        LocalLoader localLoader = new LocalLoader() {

            @Override
            public Class<?> loadClassLocal(String name, boolean resolve) {
                try {
                    return classLoader.loadClass(name);
                } catch (ClassNotFoundException ex) {
                    return null;
                }
            }

            @Override
            public Package loadPackageLocal(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Resource> loadResourceLocal(String name) {
                return Collections.emptyList();
            }
        };
        Set<String> systemPaths = plugin.getSystemPaths();
        PathFilter systemFilter = plugin.getSystemFilter();
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec(systemFilter, PathFilters.acceptAll(), localLoader, systemPaths));
        specBuilder.setModuleClassLoaderFactory(new BundleReferenceClassLoader.Factory<SystemBundleState>(systemBundle));

        try {
            final ModuleSpec moduleSpec = specBuilder.create();
            ModuleLoader moduleLoader = new ModuleLoader() {

                @Override
                protected ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException {
                    return (moduleSpec.getModuleIdentifier().equals(identifier) ? moduleSpec : null);
                }

                @Override
                public String toString() {
                    return getClass().getSimpleName();
                }
            };
            return moduleLoader.loadModule(specBuilder.getIdentifier());
        } catch (ModuleLoadException ex) {
            throw MESSAGES.illegalStateCannotCreateFrameworkModule(ex);
        }
    }
}