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
 * 百度文心一言 ERNIE 大模型Provider实现<br/>
 * API文档：<a href="https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Fm2vrveyu">...</a><br/>
 * <p>
 * API Key 管理页面<br/>
 * <a href="https://console.bce.baidu.com/ai/#/ai/wenxin/workshop/overview">...</a>
 * <p>
 * 注意：百度文心 API 使用 OAuth 2.0 认证（access_token），
 * 响应格式也与 OpenAI 不同（使用 result 字段而非 choices[0].message.content）。
 *
 * @author 百岁 (baisui@qlangtech.com)
 * @date 2026/8/30
 */
public class ErnieLLMProvider extends LLMProvider {
    private static final Logger logger = LoggerFactory.getLogger(ErnieLLMProvider.class);

    /**
     * 百度文心 API 端点路径模板，{model} 会被替换为具体的模型名称
     * 不同模型使用不同的端点，如：completions_pro、ernie-4.5-8k-preview 等
     */
    private static final String URL_PATH_TEMPLATE = "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/{model}";
    public static final String DEFAULT_MODEL = "ernie-4.5";
    public static final String DEFAULT_BASE_URL = "https://aip.baidubce.com";
    /**
     * OAuth 2.0 token 端点
     */
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    @FormField(identity = true, type = FormFieldType.INPUTTEXT, ordinal = 0, validate = {Validator.require,
            Validator.identity})
    public String name;

    @FormField(type = FormFieldType.INPUTTEXT, ordinal = 1, validate = {Validator.require, Validator.url})
    public String baseUrl;

    /**
     * 百度文心 API Key（Client ID）
     */
    @FormField(type = FormFieldType.PASSWORD, ordinal = 2, validate = {Validator.require})
    public String apiKey;

    /**
     * 百度文心 Secret Key（Client Secret）
     */
    @FormField(type = FormFieldType.PASSWORD, ordinal = 3, validate = {Validator.require})
    public String secretKey;

    @FormField(type = FormFieldType.INT_NUMBER, ordinal = 4, validate = {Validator.require, Validator.integer})
    public Integer maxTokens;

    @FormField(type = FormFieldType.ENUM, ordinal = 5, validate = {Validator.require})
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
        // 文心错误格式: { "error_code": ..., "error_msg": "..." }
        if (responseJson.containsKey("error_code")) {
            int errorCode = responseJson.getIntValue("error_code");
            String errorMsg = responseJson.getString("error_msg");
            if (StringUtils.isNotEmpty(errorMsg)) {
                response.setErrorMessage("[" + errorCode + "] " + errorMsg);
            } else {
                response.setErrorMessage("Error code: " + errorCode);
            }
            return false;
        }
        response.setModel(this.getModel());
        return true;
    }

    @Override
    protected StringBuilder getResponseBodyContent(JSONObject responseJson) {
        // 文心响应格式: { "result": "...", "is_truncated": false, ... }
        // 注意：文心 API 不使用 choices 数组，而是直接使用 result 字段
        if (responseJson.containsKey("result")) {
            String result = responseJson.getString("result");
            if (result != null) {
                boolean isTruncated = responseJson.getBooleanValue("is_truncated");
                if (isTruncated) {
                    logger.warn("Response was truncated due to max_tokens limit");
                }
                return new StringBuilder(result);
            }
        }
        return null;
    }

    @Override
    protected Consumer<JSONObject> getDeltaContentConsumer(LLMOptionParams params) {
        return (data) -> {
            // 文心流式响应格式: { "result": "...", "sentence_id": ..., "is_end": ... }
            String result = data.getString("result");
            if (result != null) {
                params.getStreamOutputConsumer().accept(result);
            }
        };
    }

    @Override
    protected void processErrorResponseBody(int status, IOException e, JSONObject errBody) {
        // 文心错误格式: { "error_code": ..., "error_msg": "..." }
        if (errBody.containsKey("error_code")) {
            int errorCode = errBody.getIntValue("error_code");
            String errorMsg = errBody.getString("error_msg");
            if (StringUtils.isNotEmpty(errorMsg)) {
                throw TisException.create(String.format("ERNIE API Error [%d]: %s", errorCode, errorMsg));
            } else {
                throw TisException.create("ERNIE API Error code: " + errorCode);
            }
        } else if (errBody.containsKey("error")) {
            // OAuth 错误格式: { "error": "...", "error_description": "..." }
            String error = errBody.getString("error");
            String errorDescription = errBody.getString("error_description");
            if (StringUtils.isNotEmpty(errorDescription)) {
                throw TisException.create("ERNIE Auth Error: " + error + " - " + errorDescription);
            } else {
                throw TisException.create("ERNIE Auth Error: " + error);
            }
        }
    }

    @Override
    protected List<ConfigFileContext.Header> appendHeaders() {
        // 文心 API 不需要特殊的请求头，认证通过 URL 参数 access_token 传递
        return List.of(new ConfigFileContext.Header("Content-Type", "application/json"));
    }

    @Override
    protected void addCustomizeParams(ITISJsonSchema jsonOutput, List<String> systemPrompt, LLMOptionParams params,
                                      List<HttpUtils.PostParam> postParams) {
        // 文心 API 不支持原生 response_format 参数，JSON 输出需要通过提示词引导
        // 文心 API 支持传入 temperature、top_p、penalty_score 等参数
        // 这些参数由基类的 Sampling 机制处理，此处无需额外添加
    }

    @Override
    public String getProviderName() {
        return "Ernie";
    }

    @Override
    public boolean isAvailable() {
        return this.getApiKey() != null && !this.getApiKey().isEmpty()
                && this.secretKey != null && !this.secretKey.isEmpty();
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
        return this.maxTokens != null ? this.maxTokens : 2048;
    }

    @Override
    protected String getModel() {
        return StringUtils.isNotEmpty(this.model) ? this.model : DEFAULT_MODEL;
    }

    private String getApiKey() {
        return this.apiKey;
    }

    /**
     * 获取 access_token，使用百度 OAuth 2.0 认证
     * 通过 API Key 和 Secret Key 获取 access_token
     */
    private String getAccessToken() {
        try {
            String tokenUrl = TOKEN_URL + "?grant_type=client_credentials"
                    + "&client_id=" + this.apiKey
                    + "&client_secret=" + this.secretKey;
            // 使用 HttpUtils 获取 access_token
            String tokenResponse = com.alibaba.fastjson.JSON.parseObject(
                    org.apache.commons.io.IOUtils.toString(
                            new java.net.URL(tokenUrl).openStream(),
                            com.qlangtech.tis.manage.common.TisUTF8.get()
                    )
            ).getString("access_token");
            if (StringUtils.isEmpty(tokenResponse)) {
                throw new IllegalStateException("Failed to get access_token from ERNIE API");
            }
            return tokenResponse;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to get access_token from ERNIE API", ex);
        }
    }

    @Override
    protected URL getApiUrl() {
        try {
            // 百度文心 API 需要将 access_token 作为 URL 参数传递
            // 不同模型使用不同的端点路径，模型名称决定了端点
            String modelName = getModel();
            String base = StringUtils.isNotEmpty(this.baseUrl) ? this.baseUrl : DEFAULT_BASE_URL;

            // 文心模型端点映射：不同模型使用不同的端点名称
            String endpointPath = URL_PATH_TEMPLATE.replace("{model}", modelName);

            // 获取 access_token 并拼接 URL
            String accessToken = getAccessToken();
            return new URL(base + endpointPath + "?access_token=" + accessToken);
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
            return "Ernie";
        }

        @Override
        protected boolean validateAll(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
            return this.verify(msgHandler, context, postFormVals);
        }

        @Override
        protected boolean verify(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
            ErnieLLMProvider provider = postFormVals.newInstance();
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
            int max = 65536;
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
            return EndType.Ernie;
        }
    }
}