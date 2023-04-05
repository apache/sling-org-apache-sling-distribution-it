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

    public static JsonNode getResource(SlingClient slingClient, String path) throws JsonException, ClientException {
        if (!path.endsWith(JSON_SELECTOR)) {
            path += JSON_SELECTOR;
        }
        return slingClient.doGetJson(path, 200);
    }

    public static String assertPostResourceWithParameters(SlingClient slingClient,
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

        SlingClient slingClientRequest =  new SlingClient(slingClient.getUrl(), DISTRIBUTOR_USER, DISTRIBUTOR_PASSWORD);
        SlingHttpResponse slingHttpResponse = slingClientRequest.doPost(path, urlEncodedFormEntity, status);
        return slingHttpResponse.getContent();
    }

    private static String assertPostResource(SlingClient slingClient,
                                             int status, String path, byte[] bytes) throws ClientException {
        if (bytes == null) {
            bytes = new byte[0];
        }
        ByteArrayEntity byteArrayEntity = new ByteArrayEntity(bytes);
        SlingHttpResponse slingHttpResponse = slingClient.doPost(path, byteArrayEntity, status);
        return slingHttpResponse.getContent();
    }

    public static void setArrayProperties(SlingClient slingClient, String resource, String property, String... values) throws IOException, ClientException {
        List<String> parameters = new ArrayList<>();
        for (String value : values) {
            parameters.add(property);
            parameters.add(value);
        }

        assertPostResourceWithParameters(slingClient, 200, resource, parameters.toArray(new String[parameters.size()]));
    }

    public static void assertResponseContains(SlingClient slingClient, String resource, String... parameters) throws ClientException {
        if (!resource.endsWith(JSON_SELECTOR)) {
            resource += JSON_SELECTOR;
        }

        SlingHttpResponse slingHttpResponse = slingClient.doGet(resource, 200);
        String content = slingHttpResponse.getContent().replaceAll("\n", "").trim();

        for (String parameter : parameters) {
            assertTrue(parameter + " is not contained in " + content, content.contains(parameter));
        }
    }

    public static void distribute(SlingClient slingClient, String agentName, DistributionRequestType action, String... paths) throws IOException, ClientException {
        String agentResource = agentUrl(agentName);

        executeDistributionRequest(slingClient, 202, agentResource, action, false, paths);
    }

    public static void distributeDeep(SlingClient slingClient, String agentName, DistributionRequestType action, String... paths) throws IOException, ClientException {
        String agentResource = agentUrl(agentName);

        executeDistributionRequest(slingClient, 202, agentResource, action, true, paths);
    }

    public static String executeDistributionRequest(SlingClient slingClient, int status, String resource, DistributionRequestType action, boolean deep, String... paths) throws IOException, ClientException {

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

        return assertPostResourceWithParameters(slingClient, status, resource, args.toArray(new String[args.size()]));
    }

    public static String doExport(SlingClient slingClient, String exporterName, DistributionRequestType action, String... paths) throws IOException, ClientException {
        String exporterUrl = exporterUrl(exporterName);

        return executeDistributionRequest(slingClient, 200, exporterUrl, action, false, paths);
    }

    public static String doImport(SlingClient slingClient, String importerName, byte[] bytes) throws ClientException {
        String agentResource = importerUrl(importerName);

        return assertPostResource(slingClient, 200, agentResource, bytes);
    }

    public static void deleteNode(SlingClient slingClient, String path) throws IOException, ClientException {
        assertPostResourceWithParameters(slingClient, 200, path, ":operation", "delete");
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

    public static void assertEmptyFolder(SlingClient slingClient, String path) throws IOException, JsonException, ClientException, InterruptedException {
        if (slingClient.exists(path)) {
            int retries = 100;
            while (getChildrenForFolder(slingClient, path).size() > 0 && retries-- > 0) {
                Thread.sleep(1000);
            }
            List<String> children = getChildrenForFolder(slingClient, path);
            assertEquals(0, children.size());
        }
    }

    public static List<String> getChildrenForFolder(SlingClient slingClient, String path) throws IOException, JsonException, ClientException {
        List<String> result = new ArrayList<>();
        JsonNode authorJson = getResource(slingClient, path + ".1.json");
        Iterator<String> it = authorJson.fieldNames();
        while (it.hasNext()) {
            String key = it.next();

            if (!key.contains(":")) {
                result.add(key);
            }
        }
        return result;
    }

    public static Map<String, Map<String, Object>> getQueues(SlingClient slingClient, String agentName) throws JsonException, ClientException {
        Map<String, Map<String, Object>> result = new HashMap<>();

        JsonNode jsonNode = getResource(slingClient, queueUrl(agentName) + ".infinity");
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

    public static List<Map<String, Object>> getQueueItems(SlingClient slingClient, String queueUrl) throws JsonException, ClientException {
        List<Map<String, Object>> result = new ArrayList<>();

        JsonNode jsonNode = getResource(slingClient, queueUrl + ".infinity");

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
