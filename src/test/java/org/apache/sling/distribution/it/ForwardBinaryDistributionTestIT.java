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

import static org.apache.sling.distribution.it.DistributionUtils.assertExists;
import static org.apache.sling.distribution.it.DistributionUtils.distributeDeep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import org.apache.sling.distribution.DistributionRequestType;
import org.apache.sling.testing.clients.ClientException;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(Parameterized.class)
public class ForwardBinaryDistributionTestIT extends DistributionIntegrationTestBase {

    @Parameterized.Parameters
    public static Collection<Object[]> generateData() {
        return Arrays.asList(new Object[][] {
                //{ true },
                { false },
        });
    }

	public ForwardBinaryDistributionTestIT(boolean useSharedDatastore) throws ClientException {
        // use instances with shared datastore
		super(useSharedDatastore);
	}

	@Test
	public void testBinaryDistribution() throws Exception {
        byte[] bytes = new byte[6000];
        new Random().nextBytes(bytes);
		String nodePath = "/content/asset.txt";

		File file = new File("asset.txt");
		OutputStream outStream = new FileOutputStream(file);
		outStream.write(bytes);
		outStream.close();

		authorClient.upload(file, "application/octet-stream", nodePath, true, 200);

		assertExists(authorClient, nodePath);
        distributeDeep(authorClient, "publish", DistributionRequestType.ADD, nodePath);
        assertExists(publishClient, nodePath);
        //TODO: also inspect the package size in binaryless case
	}

	/*@AfterClass
	public static void killInstances(){
		killContainers();
	}*/
}
