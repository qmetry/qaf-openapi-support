/**
 * MIT License

Copyright (c) 2019 Infostretch Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.qmetry.qaf.automation.openapi.v3;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qmetry.qaf.automation.util.FileUtil;
import com.qmetry.qaf.automation.util.JSONUtil;
import com.qmetry.qaf.automation.util.StringUtil;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * @author chirag.jayswal
 *
 */
public class CodeGenerator {
	private String prefix;
	private String tmplKey;

	private OpenAPI api;
	private Map<String, Map<String, Object>> config;
	private XMLConfiguration dataConfig;

	private Map<String, String> globalHeaders;
	private Gson gson;
	private String specUrl;

	private StringBuffer bddSrc;

	private static final String SCENARIO_TMPL = "\n\n@key:%s\n" + "Scenario: %s\n"
			+ "When user requests \"%s\" with data \"${parameters}\"\n"
			+ "Then response should have status code \"${statusCode}\"";

	private static final String SCENARIO_TMPL2 = SCENARIO_TMPL + "\nAnd validate \"%s\" response with schema";

	public CodeGenerator(String specUrl, OpenAPI api) {
		this.api = api;
		this.specUrl = specUrl;
		gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		dataConfig = new XMLConfiguration();
		dataConfig.setDelimiterParsingDisabled(true);
		dataConfig.setAttributeSplittingDisabled(true);
		config = new LinkedHashMap<String, Map<String, Object>>();
	}

	public void generate() throws ConfigurationException, IOException {
		prefix = StringUtil.toCamelCaseIdentifier(api.getInfo().getTitle());
		tmplKey = prefix + ".tmpl";
		
		Map<String, Object> tmpl = new LinkedHashMap<String, Object>();
		config.put(tmplKey, tmpl);
		
		bddSrc = new StringBuffer("@Auto-generated \nFeature: " + api.getInfo().getTitle());
		bddSrc.append("\n").append(api.getInfo().getDescription()).append("\n");

		tmpl.put("baseUrl", api.getServers().get(0).getUrl());
		scanGlobalHeaders();

		for (Entry<String, PathItem> entry : api.getPaths().entrySet()) {
			Map<HttpMethod, Operation> opMap = entry.getValue().readOperationsMap();
			for (Entry<HttpMethod, Operation> opEntry : opMap.entrySet()) {
				Map<String, Object> params = recordReqCall(entry.getKey(), opEntry.getKey(), opEntry.getValue());
				recordResponses(entry.getKey(), opEntry.getKey(), opEntry.getValue(), params);
			}
		}

		tmpl.put("headers", globalHeaders);
		Map<String, String> globalParams = new HashMap<String, String>();
		globalParams.put("specUrl", specUrl);
		tmpl.put("parameters", gson.toJson(globalParams));
		
		JSONUtil.writeJsonObjectToFile("resources/" + prefix + ".wscj", config);
		//config.save("resources/" + prefix + ".wscj");
		dataConfig.save("resources/" + prefix + "-data.xml");
		FileUtil.write(new File("scenarios/"+prefix + "SanitySuite.feature"), bddSrc, Charset.defaultCharset());
	}

	private void recordResponses(String key, HttpMethod method, Operation operation, Map<String, Object> params) {
		String reqKey = prefix + "." + StringUtil.toCamelCaseIdentifier(operation.getOperationId()) + "."
				+ method.name().toLowerCase();
		String responseKey = reqKey + "responses.response";
		boolean hasResponseBody = false;

		for (Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
			dataConfig.addProperty(responseKey + "(-1).statusCode", entry.getKey());
			ApiResponse response = entry.getValue();
			dataConfig.addProperty(responseKey + ".recId", response.getDescription());
			Map<String, Object> recparams = new HashMap<String, Object>(params);

			if (response.getContent() != null && response.getContent().entrySet() != null) {
				Iterator<Entry<String, MediaType>> iter = response.getContent().entrySet().iterator();
				if (iter.hasNext()) {
					hasResponseBody = true;
				}
			}
			dataConfig.addProperty(responseKey + ".parameters", gson.toJson(recparams));

		}

		String s = hasResponseBody ? String.format(SCENARIO_TMPL2, responseKey, operation.getSummary(), reqKey, reqKey)
				: String.format(SCENARIO_TMPL, responseKey, operation.getSummary(), reqKey);

		bddSrc.append(s);
	}

	private Map<String, Object> recordReqCall(String path, HttpMethod method, Operation operation) {
		String rcPrefix = prefix + "." + StringUtil.toCamelCaseIdentifier(operation.getOperationId()) + "."
				+ method.name().toLowerCase();
		
		Map<String, Object> reqCall = new LinkedHashMap<String, Object>();
		config.put(rcPrefix, reqCall);

		reqCall.put("reference", tmplKey);
		reqCall.put("endPoint", path.replace("{", "${"));
		reqCall.put("method", method.name());

		List<Parameter> params = operation.getParameters();
		Map<String, Object> parameters = new HashMap<String, Object>();
		Map<String, String> formParameters = new HashMap<String, String>();
		Map<String, String> headers = new HashMap<String, String>();

		if (null != params) {
			Map<String, String> queryParameters = new HashMap<String, String>();
			for (Parameter param : params) {
				String paramName = param.getName();
				String type = param.getSchema().getType();
				Object example = "string".equalsIgnoreCase(type) ? "string value"
						: "integer".equalsIgnoreCase(type) ? 123 : "";

				switch (param.getIn()) {
				case "header":
					if (!globalHeaders.containsKey(paramName)) {
						parameters.put(paramName, example);
						addParam(headers, paramName);
					}
					break;
				case "query":
					parameters.put(paramName, example);
					addParam(queryParameters, paramName);
					break;
				case "formData":
					parameters.put(paramName, example);
					addParam(formParameters, paramName);
					break;
				case "body":
					parameters.put("body", "");
					reqCall.put("body", "${body}");
					break;
				default:
					parameters.put(paramName, example);
					break;
				}
			}

			if (!headers.isEmpty()) {
				reqCall.put("headers", headers);
			}
			if (!queryParameters.isEmpty()) {
				reqCall.put("query-parameters", queryParameters);
			}
			if (!formParameters.isEmpty()) {
				reqCall.put("form-parameters", formParameters);
			}
		}

		if (operation.getRequestBody() != null) {
			Content content = operation.getRequestBody().getContent();
			for (Entry<String, MediaType> contenEntry : content.entrySet()) {
				if (contenEntry.getKey().contains("form")) {
					for (Object paramName : contenEntry.getValue().getSchema().getProperties().keySet()) {
						parameters.put(paramName.toString(), "");
						addParam(formParameters, paramName.toString());
					}
				}
				headers.put("Content-Type", contenEntry.getKey());
				reqCall.put("headers", headers);
			}
			if (!formParameters.isEmpty()) {
				reqCall.put("form-parameters", formParameters);
			} else {
				reqCall.put("body", "${body}");
				parameters.put("body", "");
			}
		}

		return parameters;
	}

	private void addParam(Map<String, String> params, String param) {
		params.put(param, "${" + param + "}");
	}

	private Map<String, String> scanGlobalHeaders() {
		globalHeaders = new HashMap<String, String>();
		List<PathItem> items = new ArrayList<PathItem>(api.getPaths().values());
		for (int i = 0; i < items.size(); i++) {
			PathItem item = items.get(i);
			List<Parameter> params = item.getParameters();
			if (null != params) {
				for (Parameter param : params) {
					String paramName = param.getName();
					if (param.getIn().equalsIgnoreCase("header") && !globalHeaders.containsKey(paramName)) {
						ListIterator<PathItem> iter = items.listIterator(i);
						boolean isGlobal = true;
						while (iter.hasNext() && isGlobal) {
							PathItem itemToMatch = iter.next();
							isGlobal = false;
							for (Parameter paramToMatch : itemToMatch.getParameters()) {
								if (paramName.equalsIgnoreCase(paramToMatch.getName())) {
									isGlobal = true;
									break;
								}
							}
						}
						if (isGlobal) {
							globalHeaders.put(paramName, "${" + paramName + "}");
						}
					}
				}
			}
		}
		return globalHeaders;
	}

}
