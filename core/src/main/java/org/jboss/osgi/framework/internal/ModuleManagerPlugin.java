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
import static org.osgi.framework.Constants.SYSTEM_BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.VISIBILITY_REEXPORT;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ModuleSpec.Builder;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.ClassFilter;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.FrameworkModulePlugin;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.ModuleLoaderPlugin;
import org.jboss.osgi.framework.ModuleLoaderPlugin.ModuleSpecBuilderContext;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemPathsPlugin;
import org.jboss.osgi.framework.internal.NativeCodePlugin.BundleNativeLibraryProvider;
import org.jboss.osgi.metadata.ActivationPolicyMetaData;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XBundleRequirement;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.BundleReference;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
final class ModuleManagerPlugin extends AbstractPluginService<ModuleManagerPlugin> {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<SystemPathsPlugin> injectedSystemPaths = new InjectedValue<SystemPathsPlugin>();
    private final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    private final InjectedValue<FrameworkModulePlugin> injectedFrameworkModule = new InjectedValue<FrameworkModulePlugin>();
    private final InjectedValue<ModuleLoaderPlugin> injectedModuleLoader = new InjectedValue<ModuleLoaderPlugin>();
    private Module frameworkModule;

    static void addService(ServiceTarget serviceTarget) {
        ModuleManagerPlugin service = new ModuleManagerPlugin();
        ServiceBuilder<ModuleManagerPlugin> builder = serviceTarget.addService(InternalServices.MODULE_MANGER_PLUGIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
        builder.addDependency(Services.SYSTEM_BUNDLE, SystemBundleState.class, service.injectedSystemBundle);
        builder.addDependency(IntegrationServices.FRAMEWORK_MODULE_PLUGIN, FrameworkModulePlugin.class, service.injectedFrameworkModule);
        builder.addDependency(IntegrationServices.MODULE_LOADER_PLUGIN, ModuleLoaderPlugin.class, service.injectedModuleLoader);
        builder.addDependency(IntegrationServices.SYSTEM_PATHS_PLUGIN, SystemPathsPlugin.class, service.injectedSystemPaths);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private ModuleManagerPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        FrameworkModulePlugin modulePlugin = injectedFrameworkModule.getValue();
        SystemBundleState systemBundle = injectedSystemBundle.getValue();
        frameworkModule = modulePlugin.getFrameworkModule(systemBundle);
    }

    @Override
    public ModuleManagerPlugin getValue() {
        return this;
    }

    ModuleLoaderPlugin getModuleLoaderPlugin() {
        return injectedModuleLoader.getValue();
    }

    ModuleLoader getModuleLoader() {
        return getModuleLoaderPlugin().getModuleLoader();
    }

    Module getFrameworkModule() {
        return frameworkModule;
    }

    ModuleIdentifier getModuleIdentifier(final XResource res) {
        assert res != null : "Null resource";
        assert !res.isFragment() : "A fragment is not a module";

        ModuleIdentifier identifier = res.getAttachment(ModuleIdentifier.class);
        if (identifier != null)
            return identifier;

        XIdentityCapability icap = res.getIdentityCapability();
        Module module = res.getAttachment(Module.class);
        if (module != null) {
            identifier = module.getIdentifier();
        } else if (SYSTEM_BUNDLE_SYMBOLICNAME.equals(icap.getSymbolicName())) {
            identifier = getFrameworkModule().getIdentifier();
        } else {
            int revision = (res instanceof AbstractBundleRevision ? ((AbstractBundleRevision)res).getRevisionId() : 0);
            identifier = getModuleLoaderPlugin().getModuleIdentifier(res, revision);
        }

        res.addAttachment(ModuleIdentifier.class, identifier);
        return identifier;
    }

    /**
     * Get the module with the given identifier
     *
     * @return The module or null
     */
    Module getModule(ModuleIdentifier identifier) {
        if (frameworkModule.getIdentifier().equals(identifier)) {
            return frameworkModule;
        }
        try {
            return getModuleLoader().loadModule(identifier);
        } catch (ModuleLoadException ex) {
            return null;
        }
    }

    /**
     * Get the bundle for the given class
     *
     * @return The bundle or null
     */
    AbstractBundleState getBundleState(Class<?> clazz) {
        assert clazz != null : "Null clazz";

        AbstractBundleState result = null;
        ClassLoader loader = clazz.getClassLoader();
        if (loader instanceof BundleReference) {
            BundleReference bundleRef = (BundleReference) loader;
            result = AbstractBundleState.assertBundleState(bundleRef.getBundle());
        }
        if (result == null)
            LOGGER.debugf("Cannot obtain bundle for: %s", clazz.getName());
        return result;
    }

    ModuleIdentifier addModule(final XResource res, final List<Wire> wires) {
        assert res != null : "Null res";
        assert wires != null : "Null wires";
        assert !res.isFragment() : "Fragments cannot be added: " + res;

        Module module = res.getAttachment(Module.class);
        if (module != null) {
            ModuleIdentifier identifier = module.getIdentifier();
            ModuleLoaderPlugin moduleLoaderPlugin = getModuleLoaderPlugin();
            moduleLoaderPlugin.addModule(res, module);
            return identifier;
        }

        ModuleIdentifier identifier;
        XIdentityCapability icap = res.getIdentityCapability();
        if (SYSTEM_BUNDLE_SYMBOLICNAME.equals(icap.getSymbolicName())) {
            identifier = getFrameworkModule().getIdentifier();
        } else {
            HostBundleRevision hostRev = HostBundleRevision.assertHostRevision(res);
            identifier = createHostModule(hostRev, wires);
        }

        return identifier;
    }

    /**
     * Create a {@link ModuleSpec} from the given resolver module definition
     */
    private ModuleIdentifier createHostModule(final HostBundleRevision hostRev, final List<Wire> wires) {

        HostBundleState hostBundle = hostRev.getBundleState();
        List<RevisionContent> contentRoots = hostBundle.getContentRoots();

        final ModuleIdentifier identifier = getModuleIdentifier(hostRev);
        final ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);
        final Map<ModuleIdentifier, DependencySpec> moduleDependencies = new LinkedHashMap<ModuleIdentifier, DependencySpec>();

        // Add a system dependency
        SystemPathsPlugin plugin = injectedSystemPaths.getValue();
        Set<String> bootPaths = plugin.getBootDelegationPaths();
        PathFilter bootFilter = plugin.getBootDelegationFilter();
        PathFilter acceptAll = PathFilters.acceptAll();
        specBuilder.addDependency(DependencySpec.createSystemDependencySpec(bootFilter, acceptAll, bootPaths));

        // Map the dependency for (the likely) case that the same exporter is choosen for multiple wires
        Map<XResource, ModuleDependencyHolder> specHolderMap = new LinkedHashMap<XResource, ModuleDependencyHolder>();

        // For every {@link XWire} add a dependency on the exporter
        processModuleWireList(wires, specHolderMap);

        // Process fragment wires
        Set<String> allPaths = new HashSet<String>();

        // Add the holder values to dependencies
        for (ModuleDependencyHolder holder : specHolderMap.values()) {
            moduleDependencies.put(holder.getIdentifier(), holder.create());
        }

        // Add the module dependencies to the builder
        for (DependencySpec dep : moduleDependencies.values())
            specBuilder.addDependency(dep);

        // Add resource roots the local bundle content
        for (RevisionContent revContent : contentRoots) {
            ResourceLoader resLoader = new RevisionContentResourceLoader(revContent);
            specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resLoader));
            allPaths.addAll(resLoader.getPaths());
        }

        // Process fragment local content and more resource roots
        Set<FragmentBundleRevision> fragRevs = hostRev.getAttachedFragments();
        for (FragmentBundleRevision fragRev : fragRevs) {
            for (RevisionContent revContent : fragRev.getContentList()) {
                ResourceLoader resLoader = new RevisionContentResourceLoader(revContent);
                specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resLoader));
                allPaths.addAll(resLoader.getPaths());
            }
        }

        // Get the set of imported paths
        Set<String> importedPaths = new HashSet<String>();
        for (ModuleDependencyHolder holder : specHolderMap.values()) {
            Set<String> paths = holder.getImportPaths();
            if (paths != null) {
                importedPaths.addAll(paths);
            }
        }

        // Setup the local loader dependency
        PathFilter importFilter = acceptAll;
        PathFilter exportFilter = acceptAll;
        if (importedPaths.isEmpty() == false) {
            importFilter = PathFilters.not(PathFilters.in(importedPaths));
        }
        PathFilter resImportFilter = PathFilters.acceptAll();
        PathFilter resExportFilter = PathFilters.acceptAll();
        ClassFilter classImportFilter = new ClassFilter() {
            @Override
            public boolean accept(String className) {
                return true;
            }
        };
        final PathFilter cefPath = getExportClassFilter(hostRev);
        ClassFilter classExportFilter = new ClassFilter() {
            @Override
            public boolean accept(String className) {
                return cefPath.accept(className);
            }
        };
        LOGGER.tracef("createLocalDependencySpec: [if=%s,ef=%s,rif=%s,ref=%s,cf=%s]", importFilter, exportFilter, resImportFilter, resExportFilter, cefPath);
        DependencySpec localDep = DependencySpec.createLocalDependencySpec(importFilter, exportFilter, resImportFilter, resExportFilter, classImportFilter,
                classExportFilter);
        specBuilder.addDependency(localDep);

        // Native - Hack
        addNativeResourceLoader(hostRev, specBuilder);

        PathFilter lazyActivationFilter = getLazyPackagesFilter(hostBundle);
        specBuilder.setModuleClassLoaderFactory(new HostBundleClassLoader.Factory(hostBundle, lazyActivationFilter));
        specBuilder.setFallbackLoader(new FallbackLoader(hostRev, identifier, importedPaths));

        ModuleSpecBuilderContext context = new ModuleSpecBuilderContext() {

            @Override
            public XResource getBundleRevision() {
                return hostRev;
            }

            @Override
            public Builder getModuleSpecBuilder() {
                return specBuilder;
            }

            @Override
            public Map<ModuleIdentifier, DependencySpec> getModuleDependencies() {
                return Collections.unmodifiableMap(moduleDependencies);
            }
        };

        // Add integration dependencies, build the spec and add it to the module loader
        ModuleLoaderPlugin moduleLoaderPlugin = getModuleLoaderPlugin();
        moduleLoaderPlugin.addIntegrationDependencies(context);
        moduleLoaderPlugin.addModuleSpec(hostRev, specBuilder.create());

        return identifier;
    }

    private void processModuleWireList(List<Wire> wires, Map<XResource, ModuleDependencyHolder> depBuilderMap) {

        // A bundle may both import packages (via Import-Package) and require one
        // or more bundles (via Require-Bundle), but if a package is imported via
        // Import-Package, it is not also visible via Require-Bundle: Import-Package
        // takes priority over Require-Bundle, and packages which are exported by a
        // required bundle and imported via Import-Package must not be treated as
        // split packages.

        // Collect bundle and package wires
        List<Wire> bundleWires = new ArrayList<Wire>();
        List<Wire> packageWires = new ArrayList<Wire>();
        for (Wire wire : wires) {
            Requirement req = wire.getRequirement();
            XResource importer = (XResource) wire.getRequirer();
            XResource exporter = (XResource) wire.getProvider();

            // Skip dependencies on the module itself
            if (exporter == importer)
                continue;

            // Dependency for Import-Package
            if (req instanceof XPackageRequirement) {
                packageWires.add(wire);
                continue;
            }

            // Dependency for Require-Bundle
            if (req instanceof XBundleRequirement) {
                bundleWires.add(wire);
                continue;
            }
        }

        Set<String> importedPaths = new HashSet<String>();
        Set<Resource> packageExporters = new HashSet<Resource>();
        for (Wire wire : packageWires) {
            XResource exporter = (XResource) wire.getProvider();
            packageExporters.add(exporter);
            XPackageRequirement req = (XPackageRequirement) wire.getRequirement();
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            String path = VFSUtils.getPathFromPackageName(req.getPackageName());
            holder.setOptional(req.isOptional());
            holder.addImportPath(path);
            importedPaths.add(path);
        }
        PathFilter importedPathsFilter = PathFilters.in(importedPaths);

        for (Wire wire : bundleWires) {
            XResource exporter = (XResource) wire.getProvider();
            if (packageExporters.contains(exporter))
                continue;

            XBundleRequirement req = (XBundleRequirement) wire.getRequirement();
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            holder.setImportFilter(PathFilters.not(importedPathsFilter));
            holder.setOptional(req.isOptional());

            boolean reexport = VISIBILITY_REEXPORT.equals(req.getVisibility());
            if (reexport == true) {
                Set<String> exportedPaths = new HashSet<String>();
                for (Capability auxcap : exporter.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                    XPackageCapability packcap = (XPackageCapability) auxcap;
                    String path = packcap.getPackageName().replace('.', '/');
                    if (importedPaths.contains(path) == false)
                        exportedPaths.add(path);
                }
                PathFilter exportedPathsFilter = PathFilters.in(exportedPaths);
                holder.setImportFilter(exportedPathsFilter);
                holder.setExportFilter(exportedPathsFilter);
            }
        }
    }

    private PathFilter getExportClassFilter(XResource resModule) {
        PathFilter includeFilter = null;
        PathFilter excludeFilter = null;
        for (Capability auxcap : resModule.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
            XPackageCapability packageCap = (XPackageCapability) auxcap;
            String includeDirective = packageCap.getDirective(Constants.INCLUDE_DIRECTIVE);
            if (includeDirective != null) {
                String packageName = packageCap.getPackageName();
                String[] patterns = includeDirective.split(",");
                List<PathFilter> includes = new ArrayList<PathFilter>();
                for (String pattern : patterns) {
                    includes.add(PathFilters.match(packageName + "." + pattern));
                }
                includeFilter = PathFilters.any(includes);
            }
            String excludeDirective = packageCap.getDirective(Constants.EXCLUDE_DIRECTIVE);
            if (excludeDirective != null) {
                String packageName = packageCap.getPackageName();
                String[] patterns = excludeDirective.split(",");
                List<PathFilter> excludes = new ArrayList<PathFilter>();
                for (String pattern : patterns) {
                    excludes.add(PathFilters.match(packageName + "." + pattern));
                }
                excludeFilter = PathFilters.not(PathFilters.any(excludes));
            }
        }

        // Accept all classes for export if there is no filter specified
        if (includeFilter == null && excludeFilter == null)
            return PathFilters.acceptAll();

        if (includeFilter == null)
            includeFilter = PathFilters.acceptAll();

        if (excludeFilter == null)
            excludeFilter = PathFilters.rejectAll();

        return PathFilters.all(includeFilter, excludeFilter);
    }

    /**
     * Get a path filter for packages that trigger bundle activation for a host bundle with lazy ActivationPolicy
     */
    private PathFilter getLazyPackagesFilter(HostBundleState hostBundle) {

        // By default all packages are loaded lazily
        PathFilter result = PathFilters.acceptAll();

        ActivationPolicyMetaData activationPolicy = hostBundle.getActivationPolicy();
        if (activationPolicy != null) {
            List<String> includes = activationPolicy.getIncludes();
            if (includes != null) {
                Set<String> paths = new HashSet<String>();
                for (String packageName : includes)
                    paths.add(packageName.replace('.', '/'));

                result = PathFilters.in(paths);
            }

            List<String> excludes = activationPolicy.getExcludes();
            if (excludes != null) {
                // The set of packages on the exclude list determines the packages that can be loaded eagerly
                Set<String> paths = new HashSet<String>();
                for (String packageName : excludes)
                    paths.add(packageName.replace('.', '/'));

                if (includes != null)
                    result = PathFilters.all(result, PathFilters.not(PathFilters.in(paths)));
                else
                    result = PathFilters.not(PathFilters.in(paths));
            }
        }
        return result;
    }

    private void addNativeResourceLoader(HostBundleRevision hostrev, ModuleSpec.Builder specBuilder) {
        Deployment deployment = hostrev.getDeployment();
        addNativeResourceLoader(specBuilder, hostrev, deployment);
        if (hostrev instanceof HostBundleRevision) {
            for (FragmentBundleRevision fragRev : hostrev.getAttachedFragments()) {
                addNativeResourceLoader(specBuilder, hostrev, fragRev.getDeployment());
            }
        }
    }

    private void addNativeResourceLoader(ModuleSpec.Builder specBuilder, HostBundleRevision hostrev, Deployment deployment) {
        NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
        if (libMetaData != null) {
            NativeResourceLoader nativeLoader = new NativeResourceLoader();
            for (NativeLibrary library : libMetaData.getNativeLibraries()) {
                String libpath = library.getLibraryPath();
                String libfile = new File(libpath).getName();
                String libname = libfile.substring(0, libfile.lastIndexOf('.'));

                // Add the library provider to the policy
                NativeLibraryProvider libProvider = new BundleNativeLibraryProvider(hostrev, libname, libpath);
                nativeLoader.addNativeLibrary(libProvider);

                // [TODO] why does the TCK use 'Native' to mean 'libNative' ?
                if (libname.startsWith("lib")) {
                    libname = libname.substring(3);
                    libProvider = new BundleNativeLibraryProvider(hostrev, libname, libpath);
                    nativeLoader.addNativeLibrary(libProvider);
                }
            }

            specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(nativeLoader));
        }
    }

    // Get or create the dependency builder for the exporter
    private ModuleDependencyHolder getDependencyHolder(Map<XResource, ModuleDependencyHolder> depBuilderMap, XResource exporter) {
        ModuleIdentifier exporterId = getModuleIdentifier(exporter);
        ModuleDependencyHolder holder = depBuilderMap.get(exporter);
        if (holder == null) {
            holder = new ModuleDependencyHolder(exporterId);
            depBuilderMap.put(exporter, holder);
        }
        return holder;
    }

    /**
     * Load the module for the given identifier
     *
     * @throws ModuleLoadException If the module cannot be loaded
     */
    Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        if (getFrameworkModule().getIdentifier().equals(identifier))
            return getFrameworkModule();
        else
            return getModuleLoader().loadModule(identifier);
    }

    /**
     * Remove the module with the given identifier
     */
    void removeModule(XResource res, ModuleIdentifier identifier) {
        getModuleLoaderPlugin().removeModule(res, identifier);
    }

    private class ModuleDependencyHolder {

        private final ModuleIdentifier identifier;
        private DependencySpec dependencySpec;
        private Set<String> importPaths;
        private PathFilter importFilter;
        private PathFilter exportFilter;
        private boolean optional;

        ModuleDependencyHolder(ModuleIdentifier identifier) {
            this.identifier = identifier;
        }

        ModuleIdentifier getIdentifier() {
            return identifier;
        }

        void addImportPath(String path) {
            assertNotCreated();
            if (importPaths == null)
                importPaths = new HashSet<String>();

            importPaths.add(path);
        }

        Set<String> getImportPaths() {
            return importPaths;
        }

        void setImportFilter(PathFilter importFilter) {
            assertNotCreated();
            this.importFilter = importFilter;
        }

        void setExportFilter(PathFilter exportFilter) {
            assertNotCreated();
            this.exportFilter = exportFilter;
        }

        void setOptional(boolean optional) {
            assertNotCreated();
            this.optional = optional;
        }

        DependencySpec create() {
            if (exportFilter == null) {
                exportFilter = PathFilters.rejectAll();
            }
            if (importFilter == null) {
                importFilter = (importPaths != null ? PathFilters.in(importPaths) : PathFilters.acceptAll());
            }
            Module frameworkModule = getFrameworkModule();
            ModuleLoader depLoader = (frameworkModule.getIdentifier().equals(identifier) ? frameworkModule.getModuleLoader() : getModuleLoader());
            LOGGER.tracef("createModuleDependencySpec: [id=%s,if=%s,ef=%s,loader=%s,optional=%s]", identifier, importFilter, exportFilter, depLoader, optional);
            return DependencySpec.createModuleDependencySpec(importFilter, exportFilter, depLoader, identifier, optional);
        }

        private void assertNotCreated() {
            assert dependencySpec == null : "DependencySpec already created";
        }
    }
}