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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.ModuleLoaderPlugin;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.felix.StatelessResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class ResolverPlugin extends AbstractPluginService<ResolverPlugin> {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<NativeCodePlugin> injectedNativeCode = new InjectedValue<NativeCodePlugin>();
    private final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    private final InjectedValue<ModuleLoaderPlugin> injectedModuleLoader = new InjectedValue<ModuleLoaderPlugin>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private XResolver resolver;

    static void addService(ServiceTarget serviceTarget) {
        ResolverPlugin service = new ResolverPlugin();
        ServiceBuilder<ResolverPlugin> builder = serviceTarget.addService(InternalServices.RESOLVER_PLUGIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, service.injectedEnvironment);
        builder.addDependency(InternalServices.NATIVE_CODE_PLUGIN, NativeCodePlugin.class, service.injectedNativeCode);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, service.injectedModuleManager);
        builder.addDependency(IntegrationServices.MODULE_LOADER_PLUGIN, ModuleLoaderPlugin.class, service.injectedModuleLoader);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private ResolverPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        resolver = new StatelessResolver();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        resolver = null;
    }

    @Override
    public ResolverPlugin getValue() {
        return this;
    }

    Map<Resource, List<Wire>> resolve(final Collection<? extends Resource> mandatory, final Collection<? extends Resource> optional) throws ResolutionException {
        XEnvironment env = injectedEnvironment.getValue();
        Collection<Resource> manres = filterSingletons(mandatory);
        Collection<Resource> optres = appendOptionalFragments(mandatory, optional);
        XResolveContext context = resolver.createResolverContext(env, manres, optres);
        return resolver.resolve(context);
    }

    synchronized void resolveAndApply(Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) throws ResolutionException {
        Map<Resource, List<Wire>> wiremap = resolve(mandatory, optional);
        for (Entry<Resource, Wiring> entry : applyResolverResults(wiremap).entrySet()) {
            XResource res = (XResource) entry.getKey();
            Wiring wiring = entry.getValue();
            res.addAttachment(Wiring.class, wiring);
        }
    }

    private Collection<Resource> appendOptionalFragments(Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) {
        Collection<Capability> hostcaps = getHostCapabilities(mandatory);
        Collection<Resource> result = new HashSet<Resource>();
        if (hostcaps.isEmpty() == false) {
            result.addAll(optional != null ? optional : Collections.<Resource>emptySet());
            result.addAll(findAttachableFragments(hostcaps));
        }
        return result;
    }

    private Collection<Capability> getHostCapabilities(Collection<? extends Resource> resources) {
        Collection<Capability> result = new HashSet<Capability>();
        for (Resource res : resources) {
            List<Capability> caps = res.getCapabilities(HostNamespace.HOST_NAMESPACE);
            if (caps.size() == 1)
                result.add(caps.get(0));
        }
        return result;
    }

    private Collection<Resource> filterSingletons(Collection<? extends Resource> resources) {
        Map<String, Resource> singletons = new HashMap<String, Resource>();
        List<Resource> result = new ArrayList<Resource>(resources);
        Iterator<Resource> iterator = result.iterator();
        while (iterator.hasNext()) {
            XResource xres = (XResource) iterator.next();
            XIdentityCapability icap = xres.getIdentityCapability();
            if (icap.isSingleton()) {
                if (singletons.get(icap.getSymbolicName()) != null) {
                    iterator.remove();
                } else {
                    singletons.put(icap.getSymbolicName(), xres);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private Collection<? extends Resource> findAttachableFragments(Collection<? extends Capability> hostcaps) {
        Set<Resource> result = new HashSet<Resource>();
        XEnvironment env = injectedEnvironment.getValue();
        for (Resource res : env.getResources(Collections.singleton(IdentityNamespace.TYPE_FRAGMENT))) {
            Requirement req = res.getRequirements(HostNamespace.HOST_NAMESPACE).get(0);
            XRequirement xreq = (XRequirement) req;
            for (Capability cap : hostcaps) {
                if (xreq.matches((XCapability) cap)) {
                    result.add(res);
                }
            }
        }
        if (result.isEmpty() == false) {
            LOGGER.debugf("Adding attachable fragments: %s", result);
        }
        return result;
    }

    private Map<Resource, Wiring> applyResolverResults(Map<Resource, List<Wire>> wiremap) throws ResolutionException {

        // [TODO] Revisit how we apply the resolution results
        // An exception in one of the steps may leave the framework partially modified

        // Attach the fragments to host
        attachFragmentsToHost(wiremap);

        try {

            // Resolve native code libraries if there are any
            resolveNativeCodeLibraries(wiremap);

        } catch (BundleException ex) {
            throw new ResolutionException(ex);
        }

        // For every resolved host bundle create the {@link ModuleSpec}
        addModules(wiremap);

        // For every resolved host bundle create a {@link Module} service
        createModuleServices(wiremap);

        // Change the bundle state to RESOLVED
        setBundleToResolved(wiremap);

        // Construct and apply the resource wiring map
        XEnvironment env = injectedEnvironment.getValue();
        return env.updateWiring(wiremap);
    }

    private void attachFragmentsToHost(Map<Resource, List<Wire>> wiremap) {
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            if (res.isFragment()) {
                FragmentBundleRevision fragRev = (FragmentBundleRevision) res;
                for (Wire wire : entry.getValue()) {
                    Capability cap = wire.getCapability();
                    if (HostNamespace.HOST_NAMESPACE.equals(cap.getNamespace())) {
                        HostBundleRevision hostRev = (HostBundleRevision) cap.getResource();
                        fragRev.attachToHost(hostRev);
                    }
                }
            }
        }
    }

    private void resolveNativeCodeLibraries(Map<Resource, List<Wire>> wiremap) throws BundleException {
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            if (res instanceof UserBundleRevision) {
                UserBundleRevision userRev = (UserBundleRevision) res;
                Deployment deployment = userRev.getDeployment();

                // Resolve the native code libraries, if there are any
                NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
                if (libMetaData != null) {
                    NativeCodePlugin nativeCodePlugin = injectedNativeCode.getValue();
                    nativeCodePlugin.resolveNativeCode(userRev);
                }
            }
        }
    }

    private void addModules(Map<Resource, List<Wire>> wiremap) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            if (res.isFragment() == false) {
                List<Wire> wires = wiremap.get(res);
                ModuleIdentifier identifier = moduleManager.addModule(res, wires);
                res.addAttachment(ModuleIdentifier.class, identifier);
            }
        }
    }

    private void createModuleServices(Map<Resource, List<Wire>> wiremap) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        ModuleLoaderPlugin moduleLoader = injectedModuleLoader.getValue();
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            Bundle bundle = res.getAttachment(Bundle.class);
            if (bundle != null && bundle.getBundleId() != 0 && !res.isFragment()) {
                ModuleIdentifier identifier = moduleManager.getModuleIdentifier(res);
                moduleLoader.createModuleService(res, identifier);
            }
        }
    }

    private void setBundleToResolved(Map<Resource, List<Wire>> wiremap) {
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            if (entry.getKey() instanceof AbstractBundleRevision) {
                AbstractBundleRevision brev = (AbstractBundleRevision) entry.getKey();
                AbstractBundleState bundleState = brev.getBundleState();
                bundleState.changeState(Bundle.RESOLVED);
                // Activate the service that represents bundle state RESOLVED
                ServiceName serviceName = bundleState.getServiceName(Bundle.RESOLVED);
                bundleManager.setServiceMode(serviceName, Mode.ACTIVE);
            }
        }
    }
}