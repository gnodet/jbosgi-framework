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
package org.jboss.osgi.framework.plugin.internal;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.ClassifyingModuleLoader;
import org.jboss.modules.LocalDependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleDependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleLoaderSelector;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.PathFilters;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.AbstractRevision;
import org.jboss.osgi.framework.bundle.AbstractUserBundle;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.BundleManager.IntegrationMode;
import org.jboss.osgi.framework.bundle.FragmentRevision;
import org.jboss.osgi.framework.bundle.HostBundle;
import org.jboss.osgi.framework.bundle.OSGiModuleLoader;
import org.jboss.osgi.framework.loading.FragmentLocalLoader;
import org.jboss.osgi.framework.loading.FrameworkLocalLoader;
import org.jboss.osgi.framework.loading.JBossLoggingModuleLogger;
import org.jboss.osgi.framework.loading.ModuleClassLoaderExt;
import org.jboss.osgi.framework.loading.NativeLibraryProvider;
import org.jboss.osgi.framework.loading.NativeResourceLoader;
import org.jboss.osgi.framework.loading.VirtualFileResourceLoader;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.framework.plugin.internal.NativeCodePluginImpl.BundleNativeLibraryProvider;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XRequireBundleRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XWire;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.application.Framework;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
public class ModuleManagerPluginImpl extends AbstractPlugin implements ModuleManagerPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(ModuleManagerPluginImpl.class);

   // The framework module identifier
   private ModuleIdentifier frameworkIdentifier;
   // The module loader for the OSGi layer
   private OSGiModuleLoader moduleLoader;

   public ModuleManagerPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      moduleLoader = new OSGiModuleLoader(bundleManager);
   }

   @Override
   public void initPlugin()
   {
      // Set the {@link ModuleLogger}
      Module.setModuleLogger(new JBossLoggingModuleLogger(Logger.getLogger(ModuleClassLoader.class)));

      // Setup the {@link ClassifyingModuleLoader} if not donfigured externally
      ModuleLoader externalLoader = (ModuleLoader)getBundleManager().getProperty(ModuleLoader.class.getName());
      if (externalLoader == null)
      {
         final ModuleLoader defaultLoader = Module.getCurrentLoader();
         Module.setModuleLoaderSelector(new ModuleLoaderSelector()
         {
            @Override
            public ModuleLoader getCurrentLoader()
            {
               Map<String, ModuleLoader> delegates = new HashMap<String, ModuleLoader>();
               delegates.put(MODULE_PREFIX, moduleLoader);
               return new ClassifyingModuleLoader(delegates, defaultLoader);
            }
         });
      }
   }

   @Override
   public ModuleLoader getModuleLoader()
   {
      return moduleLoader;
   }

   @Override
   public ModuleIdentifier getModuleIdentifier(XModule resModule)
   {
      if (resModule.isFragment())
         throw new IllegalArgumentException("A fragment is not a module");

      ModuleIdentifier id = resModule.getAttachment(ModuleIdentifier.class);
      if (id != null)
         return id;

      Module module = resModule.getAttachment(Module.class);
      ModuleIdentifier identifier = (module != null ? module.getIdentifier() : null);
      if (identifier == null)
      {
         XModuleIdentity moduleId = resModule.getModuleId();
         String name = MODULE_PREFIX + "." + moduleId.getName();
         String slot = moduleId.getVersion() + "-rev" + moduleId.getRevision();
         identifier = ModuleIdentifier.create(name, slot);
      }
      
      resModule.addAttachment(ModuleIdentifier.class, identifier);
      return identifier;
   }

   @Override
   public Set<ModuleIdentifier> getModuleIdentifiers()
   {
      return moduleLoader.getModuleIdentifiers();
   }

   @Override
   public Module getModule(ModuleIdentifier identifier)
   {
      return moduleLoader.getModule(identifier);
   }

   @Override
   public AbstractRevision getBundleRevision(ModuleIdentifier identifier)
   {
      return moduleLoader.getBundleRevision(identifier);
   }

   @Override
   public AbstractBundle getBundleState(ModuleIdentifier identifier)
   {
      return moduleLoader.getBundleState(identifier);
   }

   @Override
   public ModuleIdentifier addModule(final XModule resModule)
   {
      if (resModule == null)
         throw new IllegalArgumentException("Null module");

      ModuleIdentifier identifier;
      Module module = resModule.getAttachment(Module.class);
      if (module == null)
      {
         if (resModule.getModuleId().getName().equals("system.bundle"))
         {
            ModuleSpec moduleSpec = createFrameworkSpec(resModule);
            identifier = moduleSpec.getModuleIdentifier();
         }
         else
         {
            // Get the root virtual file
            Bundle bundle = resModule.getAttachment(Bundle.class);
            HostBundle bundleState = HostBundle.assertBundleState(bundle);
            List<VirtualFile> contentRoots = bundleState.getContentRoots();

            ModuleSpec moduleSpec = createModuleSpec(resModule, contentRoots);
            identifier = moduleSpec.getModuleIdentifier();
         }
      }
      else
      {
         AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
         moduleLoader.addModule(bundleRev, module);
         identifier = module.getIdentifier();
      }
      return identifier;
   }
   
   /**
    * Create the {@link Framework} module from the give resolver module definition.
    */
   private ModuleSpec createFrameworkSpec(final XModule resModule)
   {
      if (frameworkIdentifier != null)
         throw new IllegalStateException("Framework module already created");

      frameworkIdentifier = getModuleIdentifier(resModule);
      ModuleSpec.Builder specBuilder = ModuleSpec.build(frameworkIdentifier);

      BundleManager bundleManager = getBundleManager();
      FrameworkLocalLoader frameworkLoader = new FrameworkLocalLoader(bundleManager);
      LocalDependencySpec.Builder localDependency = LocalDependencySpec.build(frameworkLoader, frameworkLoader.getExportedPaths());
      localDependency.setImportFilter(PathFilters.acceptAll()); // [TODO] Remove when this becomes the default
      localDependency.setExportFilter(PathFilters.acceptAll());
      specBuilder.addLocalDependency(localDependency.create());

      // When running in AS there are no jars on the system classpath except jboss-modules.jar
      if (bundleManager.getIntegrationMode() == IntegrationMode.CONTAINER)
      {
         String systemModules = (String)bundleManager.getProperty(PROP_JBOSS_OSGI_SYSTEM_MODULES);
         if (systemModules != null)
         {
            for (String moduleid : systemModules.split(","))
            {
               ModuleIdentifier identifier = ModuleIdentifier.create(moduleid.trim());
               ModuleDependencySpec.Builder moduleDependency = ModuleDependencySpec.build(identifier);
               moduleDependency.setExportFilter(PathFilters.acceptAll()); // re-export everything
               specBuilder.addModuleDependency(moduleDependency.create());
            }
         }
      }

      ModuleSpec frameworkSpec = specBuilder.create();
      AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
      moduleLoader.addModule(bundleRev, frameworkSpec);

      return frameworkSpec;
   }

   /**
    * Create a {@link ModuleSpec} from the given resolver module definition
    */
   public ModuleSpec createModuleSpec(final XModule resModule, List<VirtualFile> contentRoots)
   {
      ModuleSpec moduleSpec = resModule.getAttachment(ModuleSpec.class);
      if (moduleSpec == null)
      {
         ModuleIdentifier identifier = getModuleIdentifier(resModule);
         ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);

         // Add the framework module as the first required dependency
         ModuleDependencySpec.Builder frameworkDependency = ModuleDependencySpec.build(frameworkIdentifier);
         specBuilder.addModuleDependency(frameworkDependency.create());

         // Map the dependency builder for (the likely) case that the same exporter is choosen for multiple wires
         Map<XModule, DependencyBuildlerHolder> depBuilderMap = new LinkedHashMap<XModule, DependencyBuildlerHolder>();

         // In case there are no wires, there may still be dependencies due to attached fragments
         HostBundle hostBundle = resModule.getAttachment(HostBundle.class);
         if (resModule.getWires().isEmpty() && hostBundle != null)
         {
            List<FragmentRevision> fragRevs = hostBundle.getCurrentRevision().getAttachedFragments();
            for (FragmentRevision fragRev : fragRevs)
            {
               // Process the fragment wires. This would take care of Package-Imports and Require-Bundle defined on the fragment
               List<XWire> fragWires = fragRev.getResolverModule().getWires();
               processModuleWires(fragWires, depBuilderMap);

               // Create a fragment {@link LocalLoader} and add a dependency on it
               FragmentLocalLoader localLoader = new FragmentLocalLoader(fragRev);
               LocalDependencySpec.Builder localDependency = LocalDependencySpec.build(localLoader, localLoader.getPaths());
               localDependency.setImportFilter(PathFilters.acceptAll()); // [TODO] Remove when this becomes the default
               localDependency.setExportFilter(PathFilters.acceptAll());

               depBuilderMap.put(fragRev.getResolverModule(), new DependencyBuildlerHolder(localDependency));
            }
         }

         // For every {@link XWire} add a dependency on the exporter
         processModuleWires(resModule.getWires(), depBuilderMap);

         // Add the dependencies
         for (DependencyBuildlerHolder aux : depBuilderMap.values())
            aux.addDependency(specBuilder);

         // Add a local dependency for the local bundle content
         for (VirtualFile contentRoot : contentRoots)
            specBuilder.addResourceRoot(new VirtualFileResourceLoader(contentRoot));
         specBuilder.addLocalDependency();

         // Native - Hack
         Bundle bundle = resModule.getAttachment(Bundle.class);
         AbstractUserBundle bundleState = AbstractUserBundle.assertBundleState(bundle);
         Deployment deployment = bundleState.getDeployment();

         NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
         if (libMetaData != null)
         {
            NativeResourceLoader nativeLoader = new NativeResourceLoader();
            // Add the native library mappings to the OSGiClassLoaderPolicy
            for (NativeLibrary library : libMetaData.getNativeLibraries())
            {
               String libpath = library.getLibraryPath();
               String libfile = new File(libpath).getName();
               String libname = libfile.substring(0, libfile.lastIndexOf('.'));

               // Add the library provider to the policy
               NativeLibraryProvider libProvider = new BundleNativeLibraryProvider(bundleState, libname, libpath);
               nativeLoader.addNativeLibrary(libProvider);

               // [TODO] why does the TCK use 'Native' to mean 'libNative' ?
               if (libname.startsWith("lib"))
               {
                  libname = libname.substring(3);
                  libProvider = new BundleNativeLibraryProvider(bundleState, libname, libpath);
                  nativeLoader.addNativeLibrary(libProvider);
               }
            }

            specBuilder.addResourceRoot(nativeLoader);
         }

         specBuilder.setFallbackLoader(new ModuleClassLoaderExt(getBundleManager(), identifier));

         // Build the ModuleSpec
         moduleSpec = specBuilder.create();
      }

      AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
      moduleLoader.addModule(bundleRev, moduleSpec);
      return moduleSpec;
   }

   private void processModuleWires(List<XWire> wires, Map<XModule, DependencyBuildlerHolder> depBuilderMap)
   {
      for (XWire wire : wires)
      {
         XRequirement req = wire.getRequirement();
         XModule importer = wire.getImporter();
         XModule exporter = wire.getExporter();

         // Skip dependencies on the module itself
         if (exporter == importer)
            continue;

         // Skip dependencies on the system module. This is always added as the first module dependency anyway
         // [TODO] Check if the bundle still fails to resolve when it fails to declare an import on 'org.osgi.framework'
         ModuleIdentifier exporterId = getModuleIdentifier(exporter);
         if (exporterId.equals(frameworkIdentifier))
            continue;

         // Dependency for Import-Package
         if (req instanceof XPackageRequirement)
         {
            DependencyBuildlerHolder holder = getDependencyHolder(depBuilderMap, exporter);
            holder.addImportPath(VFSUtils.getPathFromPackageName(req.getName()));
            continue;
         }

         // Dependency for Require-Bundle
         if (req instanceof XRequireBundleRequirement)
         {
            DependencyBuildlerHolder holder = getDependencyHolder(depBuilderMap, exporter);
            XRequireBundleRequirement bndreq = (XRequireBundleRequirement)req;
            boolean reexport = Constants.VISIBILITY_REEXPORT.equals(bndreq.getVisibility());
            if (reexport == true)
            {
               ModuleDependencySpec.Builder moduleDependency = holder.moduleDependencyBuilder;
               moduleDependency.setExportFilter(PathFilters.acceptAll());
            }
            continue;
         }
      }
   }

   // Get or create the dependency builder for the exporter
   private DependencyBuildlerHolder getDependencyHolder(Map<XModule, DependencyBuildlerHolder> depBuilderMap, XModule exporter)
   {
      ModuleIdentifier exporterId = getModuleIdentifier(exporter);
      DependencyBuildlerHolder holder = depBuilderMap.get(exporter);
      if (holder == null)
      {
         holder = new DependencyBuildlerHolder(ModuleDependencySpec.build(exporterId));
         depBuilderMap.put(exporter, holder);
      }
      return holder;
   }

   @Override
   public Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      return moduleLoader.loadModule(identifier);
   }

   @Override
   public Module removeModule(ModuleIdentifier identifier)
   {
      return moduleLoader.removeModule(identifier);
   }

   static class DependencyBuildlerHolder
   {
      private LocalDependencySpec.Builder localDependencyBuilder;
      private ModuleDependencySpec.Builder moduleDependencyBuilder;
      private Set<String> importPaths;

      DependencyBuildlerHolder(LocalDependencySpec.Builder builder)
      {
         this.localDependencyBuilder = builder;
      }

      DependencyBuildlerHolder(ModuleDependencySpec.Builder builder)
      {
         this.moduleDependencyBuilder = builder;
      }

      void addImportPath(String path)
      {
         if (importPaths == null)
            importPaths = new HashSet<String>();

         importPaths.add(path);
      }

      void addDependency(ModuleSpec.Builder specBuilder)
      {
         if (moduleDependencyBuilder != null)
         {
            if (importPaths != null)
            {
               moduleDependencyBuilder.setImportFilter(PathFilters.in(importPaths));
            }
            specBuilder.addModuleDependency(moduleDependencyBuilder.create());
         }

         if (localDependencyBuilder != null)
         {
            specBuilder.addLocalDependency(localDependencyBuilder.create());
         }
      }
   }
}