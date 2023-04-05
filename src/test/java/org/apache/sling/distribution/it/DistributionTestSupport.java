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

import org.apache.http.Header;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.sling.testing.paxexam.SlingOptions;
import org.apache.sling.testing.paxexam.TestSupport;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.ExamSystem;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.TestContainer;
import org.ops4j.pax.exam.spi.PaxExamRuntime;
import org.ops4j.pax.exam.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.apache.sling.testing.paxexam.SlingOptions.awaitility;
import static org.apache.sling.testing.paxexam.SlingOptions.logback;
import static org.apache.sling.testing.paxexam.SlingOptions.slingInstallerFactoryConfiguration;
import static org.apache.sling.testing.paxexam.SlingOptions.slingInstallerFactoryPackages;
import static org.apache.sling.testing.paxexam.SlingOptions.slingInstallerProviderJcr;
import static org.apache.sling.testing.paxexam.SlingOptions.slingStarterContent;
import static org.hamcrest.Matchers.equalTo;
import static org.apache.sling.testing.paxexam.SlingOptions.junit;
import static org.apache.sling.testing.paxexam.SlingOptions.slingDistribution;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.awaitility.Awaitility.await;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

/**
 * Integration test base class for distribution
 */
public abstract class DistributionTestSupport extends TestSupport {

    protected static Logger logger = LoggerFactory.getLogger(DistributionTestSupport.class);

    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin";

    static int authorPort;
    static int publisherPort;
    static int sharedAuthorPort;
    static int sharedPublisherPort;

    // AUTHOR Instance
    protected Option[] authorConfig() {
        authorPort = Integer.parseInt(System.getProperty("authorPort"));
        return new Option[]{
                baseConfig(),
                quickstart(authorPort, "author"),
                vmOption("-Dsling.run.modes=author,notshared")
        };
    }

    // PUBLISH Instance
    protected Option[] publishConfig() {
        publisherPort = Integer.parseInt(System.getProperty("publishPort"));
        return new Option[]{
                baseConfig(),
                quickstart(publisherPort, "publish"),
                vmOption("-Dsling.run.modes=publish,notshared")
        };
    }

    // SHARED AUTHOR Instance
    protected Option[] shareAuthorConfig() {
        sharedAuthorPort = Integer.parseInt(System.getProperty("sharedAuthorPort"));
        return new Option[]{
                baseConfig(),
                quickstart(sharedAuthorPort, "authorshared"),
                vmOption("-Dsling.run.modes=author,shared"),
                sharedInstanceOptions()
        };
    }

    // SHARED PUBLISH Instance
    protected Option[] sharedPublishConfig() {
        sharedPublisherPort = Integer.parseInt(System.getProperty("sharedPublisherPort"));
        return new Option[]{
                baseConfig(),
                quickstart(sharedPublisherPort, "publishshared"),
                vmOption("-Dsling.run.modes=publish,shared"),
                sharedInstanceOptions()
        };
    }

    // Default configs
    protected Option baseConfig() {

        SlingOptions.versionResolver.setVersionFromProject("org.apache.jackrabbit.vault","org.apache.jackrabbit.vault");

        return composite(
                super.baseConfiguration(),
                slingInstallerProviderJcr(),
                slingInstallerFactoryConfiguration(),
                slingInstallerFactoryPackages(),
                logback(),
                slingDistribution(),
                slingStarterContent(),
                // current distribution IT bundle
                testBundle("bundle.filename"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.distribution.sample").versionAsInProject(),
                /*newConfiguration("org.apache.sling.jcr.resource.internal.JcrSystemUserValidator")
                        .put("allow.only.system.user", false).asOption(),

                newConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist")
                        .put("whitelist.bypass", "true").asOption(),*/
                /*factoryConfiguration("org.apache.sling.jcr.repoinit.RepositoryInitializer")
                        .put("scripts", new String[]{
                                "create service user testDistributionUser\n\nset principal ACL for testDistributionUser\n    allow   jcr:all     on    /\n    allow   jcr:namespaceManagement,jcr:nodeTypeDefinitionManagement on :repository\nend",
                                "create service user distribution-agent-user\n\nset principal ACL for distribution-agent-user\n    allow   jcr:all     on    /\nend"
                        })
                        .asOption(),
                factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                        .put("user.mapping", new String[]{
                                "org.apache.sling.distribution.core:distributionService=testDistributionUser",
                                "org.apache.sling.distribution.core:defaultAgentService=[distribution-agent-user]"
                        })
                        .asOption(),*/

                // testing
                junit(),
                awaitility()
        );
    }

    protected static TestContainer startContainer(Option[] config) {
        ExamSystem testSystem;
        try {
            testSystem = PaxExamRuntime.createTestSystem(config);
        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
        TestContainer container = PaxExamRuntime.createContainer(testSystem);
        container.start();
        logger.info("Container started.");
        return container;
    }

    protected static void stopContainer(TestContainer container) {
        if (container != null) {
            container.stop();
        }
    }

    protected static void waitForPath(int httpPort, String path) {
        await().atMost(240, TimeUnit.SECONDS)
                .until(() -> tryGetPath(httpPort, path), equalTo(200));
    }

    private static int tryGetPath(int httpPort, String path) {
        String url = String.format("http://localhost:%s%s.json", httpPort, path);
        HttpGet httpGet = new HttpGet(url);
        Header authHeader;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            authHeader = new BasicScheme().authenticate(new UsernamePasswordCredentials(DEFAULT_USERNAME, DEFAULT_PASSWORD), httpGet, null);
            httpGet.addHeader(authHeader);

            CloseableHttpResponse response = client.execute(httpGet);
            int status =  response.getStatusLine().getStatusCode();
            logger.info("waitForPath: url {}, status {}", url, status);
            return status;

        } catch (Exception ex) {
            logger.error("Cannot get path {}", url, ex);
        }
        return  -1;
    }

    private Option sharedInstanceOptions() {
        return composite(newConfiguration("org.apache.jackrabbit.oak.plugins.blob.datastore.FileDataStore")
                        .put("minRecordLength", "4096")
                        .put("path", "../datastore")
                        .asOption(),
                newConfiguration("org.apache.jackrabbit.oak.plugins.segment.SegmentNodeStoreService")
                        .put("customBlobStore", "true")
                        .asOption());
    }

    private String workDir(String instanceName) {
        return String.format("%s/target/paxexam/%s", PathUtils.getBaseDir(), instanceName);
    }

    protected Option quickstart(int httpPort, String instanceName) {
        final String workingDirectory = workDir(instanceName);
        return composite(
                slingQuickstartOakTar(workingDirectory, httpPort),
                CoreOptions.workingDirectory(workDir(instanceName)),
                systemProperty("sling.testing.paxexam.workingDirectory").value(workDir(instanceName))
        );
    }
}
