package org.jboss.test.osgi.framework.xservice;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.Collection;

import javax.inject.Inject;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.framework.TypeAdaptor;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.XResourceBuilderFactory;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Requirement;
import org.osgi.service.resolver.ResolutionException;

/**
 * Test Module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 14-Mar-2012
 */
public class InstallModuleTestCase extends OSGiFrameworkTest {

    @Test
    public void testInstallModule() throws Exception {

        // Try to start the bundle and verify the expected ResolutionException
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            fail("BundleException expected");
        } catch (BundleException ex) {
            ResolutionException cause = (ResolutionException) ex.getCause();
            Collection<Requirement> reqs = cause.getUnresolvedRequirements();
            assertEquals(1, reqs.size());
            Requirement req = reqs.iterator().next();
            String namespace = req.getNamespace();
            assertEquals(PackageNamespace.PACKAGE_NAMESPACE, namespace);
            assertEquals("javax.inject", req.getAttributes().get(namespace));
        }

        // Build the Module resource
        ModuleIdentifier identifier = ModuleIdentifier.create("javax.inject.api");
        Module module = Module.getBootModuleLoader().loadModule(identifier);
        XResource res = XResourceBuilderFactory.create().loadFrom(module).getResource();
        assertEquals(3, res.getCapabilities(null).size());
        assertEquals(1, res.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE).size());
        assertEquals(2, res.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE).size());
        assertEquals("javax.inject.api", res.getIdentityCapability().getSymbolicName());
        assertEquals(Version.emptyVersion, res.getIdentityCapability().getVersion());
        assertEquals(IdentityNamespace.TYPE_UNKNOWN, res.getIdentityCapability().getType());

        // Install the resource into the environment
        XEnvironment env = ((TypeAdaptor) getSystemContext()).adapt(XEnvironment.class);
        env.installResources(res);

        bundleA.start();
        assertLoadClass(bundleA, Inject.class.getName());
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages("javax.inject");
                return builder.openStream();
            }
        });
        return archive;
    }
}