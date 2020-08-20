/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.opendestro.kafkaAdapter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class Helper {
    private static HttpURLConnection httpConnection;
    private static final Logger LOG = LogManager.getLogger(Helper.class);

    public static String makeRequest(Target target) {
        BufferedReader reader;
        String line;
        StringBuilder response = new StringBuilder();
        try {
            URL url = new URL(target.getUrl());
            httpConnection = (HttpURLConnection) url.openConnection();

            httpConnection.setRequestMethod("GET");
            httpConnection.setConnectTimeout(10000);
            httpConnection.setReadTimeout(10000);

            int status = httpConnection.getResponseCode();
            reader = (status != 200) ? new BufferedReader(new InputStreamReader(httpConnection.getErrorStream())) : new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        } catch (UnknownHostException e) {
            LOG.error("Unknown host found: {}", target.getUrl(), e);
        } catch (IOException e) {
            LOG.error("IOException found when reading response: ", e);
        } finally {
            httpConnection.disconnect();
        }
        return null;
    }

    public static String convertJsonNodeToString(JsonNode jsonNode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object json = mapper.readValue(jsonNode.toString(), Object.class);
            String res = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            JSONObject object = new JSONObject();
            object.put("text", res);
            return object.toString();
        } catch (Exception e) {
            LOG.error("Exception found when converting JsonNode to String:", e);
            return null;
        }
    }

    public static boolean postToSlackWebHook(JsonNode node, String webhookUrl) {
        String val = convertJsonNodeToString(node);
        if (val == null) {
            return false;
        }
        byte[] postBody = val.getBytes(StandardCharsets.UTF_8);
        int responseCode = -1;
        try {
            httpConnection = (HttpURLConnection) new URL(webhookUrl).openConnection();
            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod("POST");
            httpConnection.setRequestProperty("User-Agent", "Java client");
            httpConnection.setRequestProperty("Content-Type", "application/json");

            try (DataOutputStream wr = new DataOutputStream(httpConnection.getOutputStream())) {
                wr.write(postBody);
                wr.flush();
            } catch (IOException e) {
                LOG.error("Exception found when handling output stream", e);
                return false;
            }
            responseCode = httpConnection.getResponseCode();
        } catch (IOException e) {
            LOG.error("Couldn't resolve host {}", httpConnection.getURL(), e);
            return false;
        } finally {
            httpConnection.disconnect();
        }
        return responseCode == 200;
    }
}
