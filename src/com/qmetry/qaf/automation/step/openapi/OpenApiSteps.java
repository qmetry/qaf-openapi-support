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
package com.qmetry.qaf.automation.step.openapi;

import static com.qmetry.qaf.automation.core.ConfigurationManager.getBundle;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request.Method;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReport.Message;
import com.qmetry.qaf.automation.core.MessageTypes;
import com.qmetry.qaf.automation.step.QAFTestStep;
import com.qmetry.qaf.automation.util.Reporter;
import com.qmetry.qaf.automation.ws.WsRequestBean;
import com.qmetry.qaf.automation.ws.rest.RestTestBase;

/**
 * @author chirag.jayswal
 *
 */
public class OpenApiSteps {

	@QAFTestStep(description = "validate {requestCall} response with schema")
	public static boolean validateResponseSchema(Object requestCall) {

		WsRequestBean bean = new WsRequestBean();
		bean.fillData(requestCall);
		bean.resolveParameters(null);

		String specUrl = bean.getParameters().getOrDefault("specUrl", getBundle().getString("openapi.specUrl"))
				.toString();

		OpenApiInteractionValidator validator = new OpenApiInteractionValidator.Builder()
				.withApiSpecificationUrl(specUrl).build();

		com.qmetry.qaf.automation.ws.Response res = new RestTestBase().getResponse();
		Response response = new SimpleResponse.Builder(res.getStatus().getStatusCode())
				.withContentType(res.getMediaType().toString()).withBody(res.getMessageBody()).build();
		ValidationReport result = validator.validateResponse(bean.getEndPoint().replace("${", "{"),
				Method.valueOf(bean.getMethod()), response);
		if (result.hasErrors()) {
			for (Message message : result.getMessages()) {
				Reporter.log(message.getMessage(), MessageTypes.Fail);
			}
		}
		return !result.hasErrors();
	}
}
