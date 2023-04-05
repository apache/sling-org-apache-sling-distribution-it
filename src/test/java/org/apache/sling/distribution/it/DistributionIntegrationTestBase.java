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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import javax.json.JsonException;

import org.apache.sling.testing.clients.ClientException;
import org.apache.sling.testing.clients.SlingClient;
import org.junit.After;
import org.ops4j.pax.exam.TestContainer;

import static org.apache.sling.distribution.it.DistributionUtils.agentUrl;
import static org.apache.sling.distribution.it.DistributionUtils.assertEmptyFolder;
import static org.apache.sling.distribution.it.DistributionUtils.assertExists;
import static org.apache.sling.distribution.it.DistributionUtils.authorAgentConfigUrl;
import static org.apache.sling.distribution.it.DistributionUtils.exporterUrl;
import static org.apache.sling.distribution.it.DistributionUtils.importerUrl;
import static org.apache.sling.distribution.it.DistributionUtils.setArrayProperties;
import static org.awaitility.Awaitility.await;

/**
 * Integration test base class for distribution
 */
public abstract class DistributionIntegrationTestBase extends DistributionTestSupport {

    private static TestContainer authorContainer;
    private static TestContainer publishContainer;
    private static TestContainer authorSharedContainer;
    private static TestContainer publishSharedContainer;

    protected SlingClient authorClient;
    protected SlingClient publishClient;

    public static final String DISTRIBUTOR_USER = "testDistributorUser";

    protected DistributionIntegrationTestBase() {
        this(false);
    }

    protected DistributionIntegrationTestBase(boolean useShared) {
        try {
            startContainers(useShared);
        } catch (ClientException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void startContainers(boolean useShared) throws ClientException, URISyntaxException {
        if (useShared) { //spin up shared instances
            if (authorSharedContainer == null) {
                authorSharedContainer = startContainer(shareAuthorConfig());
            }
            if (publishSharedContainer == null) {
                publishSharedContainer = startContainer(sharedPublishConfig());
            }
            //wait for shared author
            waitForPath(sharedAuthorPort, "/libs/sling/distribution/settings/agents/publish");
            //wait for shared publisher
            waitForPath(sharedPublisherPort, "/libs/sling/distribution/settings/agents/reverse");
            authorClient = new SlingClient(new URI("http://localhost:" + sharedAuthorPort), DEFAULT_USERNAME, DEFAULT_PASSWORD);
            publishClient = new SlingClient(new URI("http://localhost:" + sharedPublisherPort), DEFAULT_USERNAME, DEFAULT_PASSWORD);

        } else { //spin up not-shared instances
            if (authorContainer == null) {
                authorContainer = startContainer(authorConfig());
            }
            if (publishContainer == null) {
                publishContainer = startContainer(publishConfig());
            }
            //Start up finished
            await().atMost(120, TimeUnit.SECONDS);
            //wait for author
            waitForPath(authorPort, "/libs/sling/distribution/settings/agents/publish");
            //wait for publish
            waitForPath(publisherPort, "/libs/sling/distribution/settings/agents/reverse");
            authorClient = new SlingClient(new URI("http://localhost:" + authorPort), DEFAULT_USERNAME, DEFAULT_PASSWORD);
            publishClient = new SlingClient(new URI("http://localhost:" + publisherPort), DEFAULT_USERNAME, DEFAULT_PASSWORD);
        }
        //Debugging
        logger.info("====================================================");
        logger.info("AUTHOR PORT: {}", authorPort);
        logger.info("PUBLISH PORT: {}", publisherPort);
        logger.info("AUTHOR SHARED PORT: {}", sharedAuthorPort);
        logger.info("PUBLISH SHARED PORT: {}", sharedPublisherPort);
        logger.info("====================================================");

        try {
            String remoteImporterUrl = authorClient.getUrl() + importerUrl("default");

            registerPublish("publish", "default");
            registerPublish("impersonate-publish", "default");

            registerReverse("publish-reverse", "reverse");
            registerReverse("impersonate-publish-reverse", "impersonate-reverse");

            {
                assertExists(authorClient, authorAgentConfigUrl("publish-multiple"));
                setArrayProperties(authorClient, authorAgentConfigUrl("publish-multiple"),
                        "packageImporter.endpoints", "endpoint1=" + remoteImporterUrl, "endpoint2=" + remoteImporterUrl + "badaddress");

                assertExists(authorClient, agentUrl("publish-multiple"));
                assertExists(authorClient, exporterUrl("publish-multiple-passivequeue1"));
            }

            {
                assertExists(authorClient, authorAgentConfigUrl("publish-selective"));
                setArrayProperties(authorClient, authorAgentConfigUrl("publish-selective"),
                        "packageImporter.endpoints", "publisher1=" + remoteImporterUrl);

                assertExists(authorClient, agentUrl("publish-selective"));
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @After
    public void checkNoPackagesLeft() throws IOException, JsonException, InterruptedException, ClientException {
        assertEmptyFolder(authorClient, "/var/sling/distribution/packages/default/shared");
        assertEmptyFolder(authorClient, "/var/sling/distribution/packages/default/data");
        assertEmptyFolder(authorClient, "/etc/packages/sling/distribution");

        assertEmptyFolder(publishClient, "/var/sling/distribution/packages/default/shared");
        assertEmptyFolder(publishClient, "/var/sling/distribution/packages/default/data");
        assertEmptyFolder(publishClient, "/etc/packages/sling/distribution");
    }

    public static void killContainers() {
        stopContainer(authorContainer);
        stopContainer(publishContainer);
        stopContainer(authorSharedContainer);
        stopContainer(publishSharedContainer);
    }

    public void registerPublish(String publishAgent, String remoteImporter) throws Exception {
        String remoteImporterUrl = publishClient.getUrl() + importerUrl(remoteImporter);
        assertExists(authorClient, authorAgentConfigUrl(publishAgent));

        authorClient.setPropertyString(authorAgentConfigUrl(publishAgent), "packageImporter.endpoints", remoteImporterUrl);

        assertExists(authorClient, agentUrl(publishAgent));
        assertExists(publishClient, importerUrl(remoteImporter));
    }

    public void registerReverse(String reverseAgent, String remoteExporter) throws Exception {
        String remoteExporterUrl = publishClient.getUrl() + exporterUrl(remoteExporter);
        assertExists(authorClient, authorAgentConfigUrl(reverseAgent));

        authorClient.setPropertyString(authorAgentConfigUrl(reverseAgent), "packageExporter.endpoints", remoteExporterUrl);

        assertExists(authorClient, agentUrl(reverseAgent));
        assertExists(publishClient, exporterUrl(remoteExporter));
    }
}
