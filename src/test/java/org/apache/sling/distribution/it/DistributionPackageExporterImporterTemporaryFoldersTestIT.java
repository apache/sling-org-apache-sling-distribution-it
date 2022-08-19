/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.distribution.it;

import org.apache.http.protocol.HTTP;
import org.apache.sling.distribution.DistributionRequestType;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import java.util.List;

import static org.apache.sling.distribution.it.DistributionUtils.assertEmptyFolder;
import static org.apache.sling.distribution.it.DistributionUtils.assertExists;
import static org.apache.sling.distribution.it.DistributionUtils.assertNotExists;
import static org.apache.sling.distribution.it.DistributionUtils.createRandomNode;
import static org.apache.sling.distribution.it.DistributionUtils.distribute;
import static org.apache.sling.distribution.it.DistributionUtils.doExport;
import static org.apache.sling.distribution.it.DistributionUtils.doImport;
import static org.apache.sling.distribution.it.DistributionUtils.getChildrenForFolder;
import static org.junit.Assert.assertEquals;

public class DistributionPackageExporterImporterTemporaryFoldersTestIT extends DistributionIntegrationTestBase {

    @Test
    public void testAddExportImportTemp() throws Exception {

        List<String> jcrPackages;

        String nodePath = createRandomNode(publishClient, "/content/export_" + System.nanoTime());
        assertExists(publishClient, nodePath);

        distribute(publishClient, "temp", DistributionRequestType.ADD, nodePath);

        jcrPackages =  getChildrenForFolder(publishClient, "/var/sling/distribution/packages/tempvlt/data");
        assertEquals(1, jcrPackages.size());

        publishClient.deletePath(nodePath);
        assertNotExists(publishClient, nodePath);

        String content = doExport(publishClient, "temp", DistributionRequestType.PULL);
        assertEmptyFolder(publishClient, "/var/sling/distribution/packages/tempvlt/data");

        doImport(publishClient, "temp", content.getBytes(HTTP.DEFAULT_CONTENT_CHARSET));
        assertExists(publishClient, nodePath);
    }

    /*@AfterClass
    public static void killInstances(){
        killContainers();
    }*/
}
