package org.jboss.test.osgi.framework.classloader;
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

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.classloader.support.a.BarImpl;
import org.jboss.test.osgi.framework.classloader.support.a.QuxBar;
import org.jboss.test.osgi.framework.classloader.support.a.QuxFoo;
import org.jboss.test.osgi.framework.classloader.support.a.QuxImpl;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.io.InputStream;

/**
 * [MODULES-69] Allow for OSGi style Class Filtering
 *
 * @author Thomas.Diesler@jboss.com
 * @since 28-Apr-2011
 */
public class ExportFilterTestCase extends OSGiFrameworkTest {

    @Test
    public void testClassFilter() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        Bundle bundleB = installBundle(getBundleB());

        assertLoadClass(bundleA, QuxFoo.class.getName());
        assertLoadClass(bundleA, QuxBar.class.getName());
        assertLoadClass(bundleA, QuxImpl.class.getName());
        assertLoadClass(bundleA, BarImpl.class.getName());

        assertLoadClass(bundleB, QuxFoo.class.getName());
        assertLoadClass(bundleB, QuxBar.class.getName());
        assertLoadClassFail(bundleB, QuxImpl.class.getName());
        assertLoadClass(bundleB, BarImpl.class.getName());

        bundleA.uninstall();
        bundleB.uninstall();
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.addClasses(QuxBar.class, QuxFoo.class, QuxImpl.class, BarImpl.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                String packageName = QuxBar.class.getPackage().getName();
                builder.addExportPackages(packageName + ";include:=\"Qux*,BarImpl\";exclude:=QuxImpl");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleB");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages(QuxBar.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
