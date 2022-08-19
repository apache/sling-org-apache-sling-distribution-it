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


import org.apache.sling.distribution.DistributionRequestType;
import org.apache.sling.testing.clients.ClientException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.apache.sling.distribution.it.DistributionUtils.assertExists;
import static org.apache.sling.distribution.it.DistributionUtils.assertPostResourceWithParameters;
import static org.apache.sling.distribution.it.DistributionUtils.createRandomNode;
import static org.apache.sling.distribution.it.DistributionUtils.distribute;
import static org.apache.sling.distribution.it.DistributionUtils.queueUrl;

/**
 * Integration test for forward distribution
 */
public class MultipleQueueDistributionTestIT extends DistributionIntegrationTestBase {

    final static String DELETE_LIMIT = "100";

    @Ignore
    @Test
    public void testQueues() throws Exception {
        Map<String, Map<String, Object>> queues = DistributionUtils.getQueues(authorClient, "queue-multiple");
        assertEquals(2, queues.size());
        assertNotNull(queues.get("endpoint1"));
        assertEquals(true, queues.get("endpoint1").get("empty"));
        assertNotNull(queues.get("endpoint2"));
        assertEquals(true, queues.get("endpoint2").get("empty"));
    }

    @Ignore
    @Test
    public void testDistributeQueues() throws Exception {

        String nodePath = createRandomNode(authorClient, "/content/forward_add_" + System.nanoTime());
        assertExists(authorClient, nodePath);

        // Add two items in both queues
        distribute(authorClient, "queue-multiple", DistributionRequestType.ADD, nodePath);
        distribute(authorClient, "queue-multiple", DistributionRequestType.DELETE, nodePath);

        Map<String, Map<String, Object>> queues = DistributionUtils.getQueues(authorClient, "queue-multiple");
        List<Map<String, Object>> firstQueueItems = DistributionUtils.getQueueItems(authorClient, queueUrl("queue-multiple") + "/endpoint1");
        List<Map<String, Object>> secondQueueItems = DistributionUtils.getQueueItems(authorClient, queueUrl("queue-multiple") + "/endpoint2");

        assertEquals(2, queues.size());
        assertEquals(false, queues.get("endpoint1").get("empty"));
        assertEquals(2, queues.get("endpoint1").get("itemsCount"));
        assertEquals(false, queues.get("endpoint2").get("empty"));
        assertEquals(2, queues.get("endpoint2").get("itemsCount"));
        assertEquals(2, firstQueueItems.size());
        assertEquals("ADD", firstQueueItems.get(0).get("action"));
        assertEquals(DISTRIBUTOR_USER, firstQueueItems.get(0).get("userid"));
        assertEquals("DELETE", firstQueueItems.get(1).get("action"));
        assertEquals(2, secondQueueItems.size());
        assertEquals("ADD", secondQueueItems.get(0).get("action"));
        assertEquals("DELETE", secondQueueItems.get(1).get("action"));

        String secondId = (String) firstQueueItems.get(0).get("id");


        // Delete second item from endpoint1
        assertPostResourceWithParameters(authorClient, 200, queueUrl("queue-multiple") + "/endpoint1",
                "operation", "delete", "id", secondId);

        queues = DistributionUtils.getQueues(authorClient, "queue-multiple");

        assertEquals(false, queues.get("endpoint1").get("empty"));
        assertEquals(1, queues.get("endpoint1").get("itemsCount"));
        assertEquals("ADD", firstQueueItems.get(0).get("action"));
        assertEquals(false, queues.get("endpoint2").get("empty"));
        assertEquals(2, queues.get("endpoint2").get("itemsCount"));

        // Delete 2 items from endpoint2
        assertPostResourceWithParameters(authorClient, 200, queueUrl("queue-multiple") + "/endpoint2",
                "operation", "delete", "limit", "2");

        queues = DistributionUtils.getQueues(authorClient, "queue-multiple");

        assertEquals(false, queues.get("endpoint1").get("empty"));
        assertEquals(1, queues.get("endpoint1").get("itemsCount"));
        assertEquals(true, queues.get("endpoint2").get("empty"));
        assertEquals(0, queues.get("endpoint2").get("itemsCount"));
    }

    @Ignore
    @Test
    public void testCopyDistributeQueues() throws Exception {

        String nodePath = createRandomNode(authorClient, "/content/forward_add_" + System.nanoTime());
        assertExists(authorClient, nodePath);


        // Add two items in both queues
        distribute(authorClient, "queue-multiple", DistributionRequestType.ADD, nodePath);

        Map<String, Map<String, Object>> queues = DistributionUtils.getQueues(authorClient, "queue-multiple");
        assertEquals(1, queues.get("endpoint1").get("itemsCount"));
        assertEquals(1, queues.get("endpoint2").get("itemsCount"));
        assertPostResourceWithParameters(authorClient, 200, queueUrl("queue-multiple") + "/endpoint1",
                "operation", "delete", "limit", DELETE_LIMIT);

        queues = DistributionUtils.getQueues(authorClient, "queue-multiple");
        assertEquals(0, queues.get("endpoint1").get("itemsCount"));
        assertEquals(1, queues.get("endpoint2").get("itemsCount"));

        List<Map<String, Object>> firstQueueItems = DistributionUtils.getQueueItems(authorClient, queueUrl("queue-multiple") + "/endpoint1");
        List<Map<String, Object>> secondQueueItems = DistributionUtils.getQueueItems(authorClient, queueUrl("queue-multiple") + "/endpoint2");

        assertEquals(0, firstQueueItems.size());
        assertEquals(1, secondQueueItems.size());


        String secondQueueItemId = (String) secondQueueItems.get(0).get("id");

        assertPostResourceWithParameters(authorClient, 200, queueUrl("queue-multiple") + "/endpoint1",
                "operation", "copy", "id", secondQueueItemId, "from", "endpoint2");

        firstQueueItems = DistributionUtils.getQueueItems(authorClient, queueUrl("queue-multiple") + "/endpoint1");
        secondQueueItems = DistributionUtils.getQueueItems(authorClient, queueUrl("queue-multiple") + "/endpoint2");

        assertEquals(1, firstQueueItems.size());
        assertEquals(1, secondQueueItems.size());
    }


    @After
    public void clean() throws IOException, ClientException {
        assertPostResourceWithParameters(authorClient, 200, queueUrl("queue-multiple") + "/endpoint1",
                "operation", "delete", "limit", DELETE_LIMIT);

        assertPostResourceWithParameters(authorClient, 200, queueUrl("queue-multiple") + "/endpoint2",
                "operation", "delete", "limit", DELETE_LIMIT);

    }

    /*@AfterClass
    public static void killInstances(){
        killContainers();
    }*/
}
