/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qlangtech.tis.plugin.llm;

import com.alibaba.citrus.turbine.Context;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qlangtech.tis.aiagent.core.IAgentContext;
import com.qlangtech.tis.aiagent.llm.ITISJsonSchema;
import com.qlangtech.tis.aiagent.llm.LLMOptionParams;
import com.qlangtech.tis.aiagent.llm.LLMProvider;
import com.qlangtech.tis.aiagent.llm.UserPrompt;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.lang.TisException;
import com.qlangtech.tis.manage.common.ConfigFileContext;
import com.qlangtech.tis.manage.common.HttpUtils;
import com.qlangtech.tis.plugin.IEndTypeGetter;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.runtime.module.misc.IControlMsgHandler;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Consumer;

/**
 * 火山引擎豆包大模型Provider实现<br/>
 * API文档：<a href="https://www.volcengine.com/docs/82379/1262005">...</a><br/>
 * <p>
 * API Key 管理页面<br/>
 * <a href="https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey">...</a>
 *
 * @author 百岁 (baisui@qlangtech.com)
 * @date 2026/8/30
 */
public class DoubaoLLMProvider extends LLMProvider {
    private static final Logger logger = LoggerFactory.getLogger(DoubaoLLMProvider.class);

    private static final String URL_PATH = "/api/v3/chat/completions";
    public static final String DEFAULT_MODEL = "doubao-1.5-pro";
    public static final String DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com";

    @FormField(identity = true, type = FormFieldType.INPUTTEXT, ordinal = 0, validate = {Validator.require,
            Validator.identity})
    public String name;

    @FormField(type = FormFieldType.INPUTTEXT, ordinal = 1, validate = {Validator.require, Validator.url})
    public String baseUrl;

    @FormField(type = FormFieldType.PASSWORD, ordinal = 2, validate = {Validator.require})
    public String apiKey;

    @FormField(type = FormFieldType.INT_NUMBER, ordinal = 3, validate = {Validator.require, Validator.integer})
    public Integer maxTokens;

    @FormField(type = FormFieldType.ENUM, ordinal = 4, validate = {Validator.require})
    public String model;

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    protected TokenUsageSummary getTokenUsageSummary(JSONObject responseJson) {
        if (responseJson.containsKey("usage")) {
            JSONObject usage = responseJson.getJSONObject("usage");
            return new TokenUsageSummary(usage.getLongValue("prompt_tokens"), usage.getLongValue("completion_tokens"));
        }
        return null;
    }

    @Override
    protected boolean processResponseJson(LLMResponse response, JSONObject responseJson) {
        if (responseJson.containsKey("error")) {
            JSONObject errDetail = responseJson.getJSONObject("error");
            String errMessage = errDetail.getString("message");
            if (StringUtils.isNotEmpty(errMessage)) {
                response.setErrorMessage(errMessage);
                return false;
            }
        }
        response.setModel(this.getModel());
        return true;
    }

    @Override
    protected StringBuilder getResponseBodyContent(JSONObject responseJson) {
        if (responseJson.containsKey("choices")) {
            JSONArray choices = responseJson.getJSONArray("choices");
            if (!choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                String finishReason = choice.getString("finish_reason");
                if ("length".equals(finishReason)) {
                    logger.warn("Response was truncated due to max_tokens limit");
                }
                return new StringBuilder(message.getString("content"));
            }
        }
        return null;
    }

    @Override
    protected Consumer<JSONObject> getDeltaContentConsumer(LLMOptionParams params) {
        return (data) -> {
            JSONArray choices = data.getJSONArray("choices");
            for (Object c : choices) {
                if (c instanceof JSONObject choice) {
                    String content = choice.getJSONObject("delta").getString("content");
                    if (content != null) {
                        params.getStreamOutputConsumer().accept(content);
                    }
                }
            }
        };
    }

    @Override
    protected void processErrorResponseBody(int status, IOException e, JSONObject errBody) {
        if (errBody.containsKey("error")) {
            JSONObject errDetail = errBody.getJSONObject("error");
            String errMessage = errDetail.getString("message");
            if (StringUtils.isNotEmpty(errMessage)) {
                throw TisException.create(errMessage);
            }
        }
    }

    @Override
    protected List<ConfigFileContext.Header> appendHeaders() {
        return List.of(new ConfigFileContext.Header("Authorization",
                "Bearer " + getApiKey()));
    }

    @Override
    protected void addCustomizeParams(ITISJsonSchema jsonOutput, List<String> systemPrompt, LLMOptionParams params,
                                      List<HttpUtils.PostParam> postParams) {
        if (jsonOutput.isContainSchema()) {
            JSONObject responseFormat = new JSONObject();
            responseFormat.put("type", "json_object");
            postParams.add(new HttpUtils.PostParam("response_format", responseFormat));
        }
    }

    @Override
    public String getProviderName() {
        return "Doubao";
    }

    @Override
    public boolean isAvailable() {
        return this.getApiKey() != null && !this.getApiKey().isEmpty();
    }

    @Override
    public LLMProvider createConfigInstance() {
        return this;
    }

    @Override
    public String identityValue() {
        return this.name;
    }

    @Override
    protected Integer getMaxTokens() {
        return this.maxTokens;
    }

    @Override
    protected String getModel() {
        return StringUtils.isNotEmpty(this.model) ? this.model : DEFAULT_MODEL;
    }

    private String getApiKey() {
        return this.apiKey;
    }

    @Override
    protected URL getApiUrl() {
        try {
            return new URL((StringUtils.isNotEmpty(this.baseUrl) ? this.baseUrl : DEFAULT_BASE_URL) + URL_PATH);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @TISExtension
    public static final class DftDescriptor extends BasicParamsConfigDescriptor implements IEndTypeGetter {
        public DftDescriptor() {
            super(KEY_DISPLAY_NAME);
        }

        @Override
        public String getDisplayName() {
            return "Doubao";
        }

        @Override
        protected boolean validateAll(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
            return this.verify(msgHandler, context, postFormVals);
        }

        @Override
        protected boolean verify(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
            DoubaoLLMProvider provider = postFormVals.newInstance();
            try {
                LLMResponse chat = provider.chat(IAgentContext.createNull(), new UserPrompt("test", "hello"), null);
                if (!chat.isSuccess()) {
                    msgHandler.addErrorMessage(context, chat.getErrorMessage());
                    return false;
                }
            } catch (Exception e) {
                msgHandler.addErrorMessage(context, e.getMessage());
                return false;
            }
            return super.verify(msgHandler, context, postFormVals);
        }

        public boolean validateMaxTokens(IFieldErrorHandler msgHandler, Context context, String fieldName,
                                         String value) {
            int tokens = Integer.parseInt(value);
            int min = 1;
            int max = 32768;
            if (tokens < min || tokens > max) {
                msgHandler.addFieldError(context, fieldName, "必须在：" + min + "至" + max + "之间");
                return false;
            }
            return true;
        }

        public boolean validateMaxRetry(IFieldErrorHandler msgHandler, Context context, String fieldName,
                                        String value) {
            int retryCount = Integer.parseInt(value);
            if (retryCount < 1) {
                msgHandler.addFieldError(context, fieldName, "不能小于1");
                return false;
            }
            if (retryCount > 3) {
                msgHandler.addFieldError(context, fieldName, "不能大于3");
                return false;
            }
            return true;
        }

        @Override
        public EndType getEndType() {
            return EndType.Doubao;
        }
    }
}