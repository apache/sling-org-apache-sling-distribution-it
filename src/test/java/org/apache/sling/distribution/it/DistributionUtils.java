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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonException;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.jackrabbit.util.Text;
import org.apache.sling.distribution.DistributionRequestType;
import org.apache.sling.testing.clients.ClientException;
import org.apache.sling.testing.clients.SlingClient;
import org.apache.sling.testing.clients.SlingHttpResponse;
import org.apache.sling.testing.serversetup.instance.SlingInstance;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Utils class for distribution ITs
 */
public class DistributionUtils {

    private static final String JSON_SELECTOR = ".json";
    private static final String DISTRIBUTION_ROOT_PATH = "/libs/sling/distribution";
    public static final String DISTRIBUTOR_USER = "testDistributorUser";
    private static final String DISTRIBUTOR_PASSWORD = "123";

    public static JsonNode getResource(SlingInstance slingInstance, String path) throws JsonException, ClientException {
        if (!path.endsWith(JSON_SELECTOR)) {
            path += JSON_SELECTOR;
        }
        return slingInstance.getSlingClient().doGetJson(path, 200);
    }

    public static String assertPostResourceWithParameters(SlingInstance slingInstance,
                                                          int status, String path,
                                                          String... parameters) throws IOException, ClientException {
        UrlEncodedFormEntity urlEncodedFormEntity;
        if (parameters != null) {
            assertEquals(0, parameters.length % 2);
            List<NameValuePair> valuePairList = new ArrayList<>();

            for (int i = 0; i < parameters.length; i += 2) {
                valuePairList.add(new BasicNameValuePair(parameters[i], parameters[i + 1]));
            }
            urlEncodedFormEntity = new UrlEncodedFormEntity(valuePairList);

        } else {
            urlEncodedFormEntity = new UrlEncodedFormEntity(new ArrayList<>());
        }

        SlingClient slingClient =  new SlingClient(slingInstance.getSlingClient().getUrl(), DISTRIBUTOR_USER, DISTRIBUTOR_PASSWORD);
        SlingHttpResponse slingHttpResponse = slingClient.doPost(path, urlEncodedFormEntity, status);
        return slingHttpResponse.getContent();
    }

    private static String assertPostResource(SlingInstance slingInstance,
                                             int status, String path, byte[] bytes) throws ClientException {
        if (bytes == null) {
            bytes = new byte[0];
        }
        ByteArrayEntity byteArrayEntity = new ByteArrayEntity(bytes);
        SlingHttpResponse slingHttpResponse = slingInstance.getSlingClient().doPost(path, byteArrayEntity, status);
        return slingHttpResponse.getContent();
    }

    public static void setArrayProperties(SlingInstance slingInstance, String resource, String property, String... values) throws IOException, ClientException {
        List<String> parameters = new ArrayList<>();
        for (String value : values) {
            parameters.add(property);
            parameters.add(value);
        }

        assertPostResourceWithParameters(slingInstance, 200, resource, parameters.toArray(new String[parameters.size()]));
    }

    public static void assertResponseContains(SlingInstance slingInstance, String resource, String... parameters) throws ClientException {
        if (!resource.endsWith(JSON_SELECTOR)) {
            resource += JSON_SELECTOR;
        }

        SlingHttpResponse slingHttpResponse = slingInstance.getSlingClient().doGet(resource, 200);
        String content = slingHttpResponse.getContent().replaceAll("\n", "").trim();

        for (String parameter : parameters) {
            assertTrue(parameter + " is not contained in " + content, content.contains(parameter));
        }
    }

    public static void distribute(SlingInstance slingInstance, String agentName, DistributionRequestType action, String... paths) throws IOException, ClientException {
        String agentResource = agentUrl(agentName);

        executeDistributionRequest(slingInstance, 202, agentResource, action, false, paths);
    }

    public static void distributeDeep(SlingInstance slingInstance, String agentName, DistributionRequestType action, String... paths) throws IOException, ClientException {
        String agentResource = agentUrl(agentName);

        executeDistributionRequest(slingInstance, 202, agentResource, action, true, paths);
    }

    public static String executeDistributionRequest(SlingInstance slingInstance, int status, String resource, DistributionRequestType action, boolean deep, String... paths) throws IOException, ClientException {

        List<String> args = new ArrayList<>();
        args.add("action");
        args.add(action.toString());

        if (deep) {
            args.add("deep");
            args.add("true");
        }

        if (paths != null) {
            for (String path : paths) {
                args.add("path");
                args.add(path);
            }
        }

        return assertPostResourceWithParameters(slingInstance, status, resource, args.toArray(new String[args.size()]));
    }

    public static String doExport(SlingInstance slingInstance, String exporterName, DistributionRequestType action, String... paths) throws IOException, ClientException {
        String exporterUrl = exporterUrl(exporterName);

        return executeDistributionRequest(slingInstance, 200, exporterUrl, action, false, paths);
    }

    public static String doImport(SlingInstance slingInstance, String importerName, byte[] bytes) throws ClientException {
        String agentResource = importerUrl(importerName);

        return assertPostResource(slingInstance, 200, agentResource, bytes);
    }

    public static void deleteNode(SlingInstance slingInstance, String path) throws IOException, ClientException {
        assertPostResourceWithParameters(slingInstance, 200, path, ":operation", "delete");
    }

    public static void assertExists(SlingClient slingClient, String path) throws Exception {
        int retries = 100;
        while (!slingClient.exists(path) && retries-- > 0) {
            Thread.sleep(1000);
        }
        assertTrue("path " + path + " doesn't exist", slingClient.exists(path));
    }

    public static void assertNotExists(SlingClient slingClient, String path) throws Exception {
        int retries = 100;
        while (slingClient.exists(path) && retries-- > 0) {
            Thread.sleep(1000);
        }
        assertFalse("path " + path + " still exists", slingClient.exists(path));
    }

    public static String createRandomNode(SlingClient slingClient, String parentPath) throws Exception {
        String nodePath = parentPath + "/" + UUID.randomUUID();
        if (!slingClient.exists(parentPath)) {
            createNode(slingClient, parentPath);
        }
        slingClient.createNode(nodePath, "nt:unstructured");
        slingClient.setPropertyString(nodePath, "propName", "propValue");
        return nodePath;
    }

    public static void createNode(SlingClient slingClient, String path) throws ClientException {

        if (slingClient.exists(path)) {
            return;
        }

        String parentPath = Text.getRelativeParent(path, 1);
        createNode(slingClient, parentPath);

        slingClient.createNode(path, "nt:unstructured");
    }

    public static String agentRootUrl() {
        return DISTRIBUTION_ROOT_PATH + "/services/agents";
    }

    public static String agentUrl(String agentName) {
        return agentRootUrl() + "/" + agentName;
    }

    public static String queueUrl(String agentName) {
        return agentUrl(agentName) + "/queues";
    }

    public static String logUrl(String agentName) {
        return agentUrl(agentName) + "/log";
    }

    public static String authorAgentConfigUrl(String agentName) {
        return DISTRIBUTION_ROOT_PATH + "/settings/agents/" + agentName;
    }

    public static String publishAgentConfigUrl(String agentName) {
        return DISTRIBUTION_ROOT_PATH + "/settings/agents/" + agentName;
    }

    public static String importerRootUrl() {
        return DISTRIBUTION_ROOT_PATH + "/services/importers";
    }

    public static String importerUrl(String importerName) {
        return importerRootUrl() + "/" + importerName;
    }

    public static String exporterRootUrl() {
        return DISTRIBUTION_ROOT_PATH + "/services/exporters";
    }

    public static String exporterUrl(String exporterName) {
        return exporterRootUrl() + "/" + exporterName;
    }

    public static String importerConfigUrl(String importerName) {
        return DISTRIBUTION_ROOT_PATH + "/settings/importers/" + importerName;
    }

    public static String exporterConfigUrl(String exporterName) {
        return DISTRIBUTION_ROOT_PATH + "/settings/exporters/" + exporterName;
    }

    public static String triggerRootUrl() {
        return DISTRIBUTION_ROOT_PATH + "/services/triggers";
    }

    public static String triggerUrl(String triggerName) {
        return triggerRootUrl() + "/" + triggerName;
    }

    public static String triggerEventUrl(String triggerName) {
        return triggerRootUrl() + "/" + triggerName + ".event";
    }

    public static void assertEmptyFolder(SlingInstance instance, SlingClient client, String path) throws IOException, JsonException, ClientException {
        if (client.exists(path)) {
            List<String> children = getChildrenForFolder(instance, path);

            assertEquals(0, children.size());
        }
    }

    public static List<String> getChildrenForFolder(SlingInstance instance, String path) throws IOException, JsonException, ClientException {
        List<String> result = new ArrayList<>();
        JsonNode authorJson = getResource(instance, path + ".1.json");
        Iterator<String> it = authorJson.fieldNames();
        while (it.hasNext()) {
            String key = it.next();

            if (!key.contains(":")) {
                result.add(key);
            }
        }
        return result;
    }

    public static Map<String, Map<String, Object>> getQueues(SlingInstance instance, String agentName) throws JsonException, ClientException {
        Map<String, Map<String, Object>> result = new HashMap<>();

        JsonNode jsonNode = getResource(instance, queueUrl(agentName) + ".infinity");
        JsonNode items = jsonNode.get("items");

        for(int i=0; i < items.size(); i++) {
            String queueName = items.get(i).textValue();

            Map<String, Object> queueProperties = new HashMap<>();

            JsonNode queue = jsonNode.get(queueName);
            queueProperties.put("empty", queue.get("empty").booleanValue());
            queueProperties.put("itemsCount", queue.get("itemsCount").intValue());

            result.put(queueName, queueProperties);
        }

        return result;
    }

    public static List<Map<String, Object>> getQueueItems(SlingInstance instance, String queueUrl) throws JsonException, ClientException {
        List<Map<String, Object>> result = new ArrayList<>();

        JsonNode jsonNode = getResource(instance, queueUrl + ".infinity");

        Iterator<String> keys = jsonNode.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            JsonNode queueItem = jsonNode.get(key);

            if (queueItem != null && queueItem.has("id")) {

                Map<String, Object> itemProperties = new HashMap<>();

                itemProperties.put("id", queueItem.get("id").textValue());
                /*itemProperties.put("paths", JsonUtil.unbox(queueItem.get("paths")));
                itemProperties.put("action", JsonUtil.unbox(queueItem.get("action")));
                itemProperties.put("userid", JsonUtil.unbox(queueItem.get("userid")));
                itemProperties.put("attempts", JsonUtil.unbox(queueItem.get("attempts")));
                itemProperties.put("time", JsonUtil.unbox(queueItem.get("time")));
                itemProperties.put("state", JsonUtil.unbox(queueItem.get("state")));*/

                result.add(itemProperties);
            }
        }

        return result;
    }

}
